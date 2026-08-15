// 실제 GPS 수집.
//
// 브라우저의 navigator.geolocation을 감싸서, 백엔드가 받아들일 수 있는 형태의 좌표만
// 걸러서 넘겨줍니다. 원시 GPS를 그대로 올리면 대부분 서버 품질 검사에서 걸러집니다.
//
// ── 알아둘 제약 두 가지 ────────────────────────────────────────────
// 1) HTTPS(보안 컨텍스트)에서만 동작합니다. localhost는 예외지만,
//    폰에서 http://192.168.x.x:5173 으로 접속하면 브라우저가 조용히 거부합니다.
// 2) 화면을 끄거나 다른 앱으로 전환하면 브라우저 탭이 멈춰 좌표가 끊깁니다.
//    기획서의 "화면 끄고 주행"은 웹으로는 구현할 수 없고 안드로이드 포그라운드
//    서비스가 필요합니다. 웹 버전은 화면을 켠 채로만 기록됩니다.

const EARTH_RADIUS_M = 6371008.8;

/** 두 좌표 사이의 거리(m). 백엔드 GeoUtils와 같은 하버사인 공식입니다. */
export function distanceMeters(lat1, lon1, lat2, lon2) {
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) * Math.sin(dLon / 2) ** 2;
  return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(a)));
}

/** 이 브라우저·이 주소에서 GPS를 쓸 수 있는지. */
export function geolocationStatus() {
  if (typeof navigator === 'undefined' || !navigator.geolocation) {
    return { ok: false, reason: 'UNSUPPORTED', message: '이 브라우저는 위치 기능을 지원하지 않아요.' };
  }
  // localhost는 예외적으로 허용됩니다. 그 외 http:// 주소에서는 브라우저가 막습니다.
  if (!window.isSecureContext) {
    return {
      ok: false,
      reason: 'INSECURE_CONTEXT',
      message:
        'HTTPS에서만 위치를 받을 수 있어요. 폰에서 테스트하려면 https 주소로 접속해야 합니다 ' +
        '(npm run dev -- --host 만으로는 안 되고, HTTPS 설정이나 터널이 필요해요).',
    };
  }
  return { ok: true, reason: 'OK', message: '위치 사용 가능' };
}

/** 위치 권한 상태를 미리 확인합니다(지원하지 않는 브라우저면 'unknown'). */
export async function permissionState() {
  if (!navigator.permissions?.query) return 'unknown';
  try {
    const result = await navigator.permissions.query({ name: 'geolocation' });
    return result.state; // 'granted' | 'prompt' | 'denied'
  } catch {
    return 'unknown';
  }
}

/**
 * 주행 중 GPS 추적을 시작합니다.
 *
 * 서버 품질 검사에 맞춰 클라이언트에서 미리 거릅니다:
 *  - 정확도가 너무 나쁜 표본은 버립니다(서버에서 어차피 보류 처리됨)
 *  - 정지 중 GPS가 미세하게 떨리는 것은 이동으로 세지 않습니다
 *  - 시각이 뒤로 가는 표본은 버립니다(서버가 중복으로 판단)
 *
 * @param {object} opts
 * @param {(point, meta) => void} opts.onPoint  걸러진 좌표 하나. 그대로 batches에 넣으면 됩니다.
 * @param {(status) => void} opts.onStatus      상태·오류 변화
 * @param {number} [opts.maxAccuracyM=50]       이보다 오차가 큰 표본은 버림
 * @param {number} [opts.minMoveM=3]            이보다 적게 움직였으면 정지로 간주
 * @returns {() => void} 추적 중지 함수
 */
export function watchRide({ onPoint, onStatus, maxAccuracyM = 50, minMoveM = 3 }) {
  const status = geolocationStatus();
  if (!status.ok) {
    onStatus?.({ kind: 'error', ...status });
    return () => {};
  }

  let last = null; // 마지막으로 채택한 좌표
  let dropped = 0;

  const watchId = navigator.geolocation.watchPosition(
    (pos) => {
      const { latitude, longitude, accuracy, speed } = pos.coords;
      const epochMs = Math.round(pos.timestamp);

      if (accuracy != null && accuracy > maxAccuracyM) {
        dropped += 1;
        onStatus?.({
          kind: 'weak',
          message: `GPS 정확도 ${Math.round(accuracy)}m — 신호가 좋아질 때까지 기다리는 중`,
          accuracyM: accuracy,
          dropped,
        });
        return;
      }

      if (last) {
        // 시각이 안 늘어나면 같은 표본입니다. 서버가 중복으로 버리므로 여기서 먼저 거릅니다.
        if (epochMs <= last.epochMs) return;

        const moved = distanceMeters(last.lat, last.lon, latitude, longitude);
        if (moved < minMoveM) {
          onStatus?.({ kind: 'idle', message: '정지 중', accuracyM: accuracy });
          return;
        }
      }

      const point = {
        epochMs,
        lat: latitude,
        lon: longitude,
        accuracyM: accuracy ?? 999,
        // speed는 기기가 못 줄 때 null입니다. 서버는 음수를 미보고로 취급합니다.
        speedMps: speed == null || Number.isNaN(speed) ? -1 : speed,
      };

      const movedFromLast = last ? distanceMeters(last.lat, last.lon, latitude, longitude) : 0;
      last = { lat: latitude, lon: longitude, epochMs };

      onPoint?.(point, { movedM: movedFromLast, accuracyM: accuracy });
      onStatus?.({ kind: 'tracking', message: `GPS 수신 중 (오차 ${Math.round(accuracy ?? 0)}m)`, accuracyM: accuracy });
    },
    (err) => {
      const map = {
        1: { reason: 'PERMISSION_DENIED', message: '위치 권한이 거부되었어요. 브라우저 설정에서 허용해 주세요.' },
        2: { reason: 'POSITION_UNAVAILABLE', message: '위치를 확인할 수 없어요. 실외로 이동해 보세요.' },
        3: { reason: 'TIMEOUT', message: '위치 확인이 지연되고 있어요.' },
      };
      onStatus?.({ kind: 'error', ...(map[err.code] ?? { reason: 'UNKNOWN', message: err.message }) });
    },
    {
      enableHighAccuracy: true, // 자전거 주행에는 셀타워 수준 정확도로는 부족합니다
      maximumAge: 1000,         // 1초보다 오래된 캐시 좌표는 쓰지 않습니다
      timeout: 15000,
    },
  );

  return () => navigator.geolocation.clearWatch(watchId);
}
