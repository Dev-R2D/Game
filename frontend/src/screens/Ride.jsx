// 화면 ④: 주행 중
// 실제 GPS(navigator.geolocation.watchPosition)로 좌표를 받아 로컬 버퍼에 쌓고,
// N초마다 /rides/{rideId}/batches로 업로드함. GPS를 못 쓰는 환경(HTTP 등)에서는
// 더미 이동 버튼으로 대체할 수 있음.
// 이동 버튼을 누르면 로컬 버퍼에 좌표가 쌓이고, N초마다 그 버퍼를 /rides/{rideId}/batches로 업로드함.
// 화면을 새로고침해도 GET /rides/active로 진행 중인 주행을 이어받음.
// 지도 위에는 fog-of-war를 깔아두고, 자전거 캐릭터가 지나간 자리만 실시간으로 안개를 걷어냄.

import { useEffect, useRef, useState } from 'react';
import { startRide, uploadBatch, getActiveRide } from '../api';
import {
  loadVWorldMap, isVWorldMapConfigured, createVWorldMap, waitForViewer,
  addImageMarker, updateEntityPosition, setCameraCenter,
  addPolyline, setPolylinePositions, onCameraMoveEnd, latLonToPixel,
} from '../vworldMap';
import { paintMistBase, punchHole, resizeCanvasToContainer } from '../fog';
import { watchRide, geolocationStatus, distanceMeters } from '../geolocation';
import { IconChevronLeft, IconMap, IconPin } from '../components/Icons';
import riderMarkerImg from '../assets/rider-marker.png';

const BATCH_INTERVAL_MS = 8000; // N초마다 배치 업로드
const START_LAT = 37.23;
const START_LON = 127.12;
const RIDE_REVEAL_RADIUS = 75; // px, 라이더가 지나간 자리 하나가 걷어내는 안개 반경
const MAP_EL_ID = 'r2d-ride-vworld';
const RIDER_MARKER_ID = 'rider';
const ROUTE_LINE_ID = 'route';

// 러닝/사이클 앱(Strava 등)의 "달린 경로 카드" 느낌으로 지도 위에 얹는 HUD.
function formatPace(secPerKm) {
  if (!Number.isFinite(secPerKm) || secPerKm <= 0) return '–:––';
  const m = Math.floor(secPerKm / 60);
  const s = Math.round(secPerKm % 60);
  return `${m}:${String(s).padStart(2, '0')}`;
}

export default function Ride({ regionCode, deckCardCodes, onFinish, onCancel }) {
  const [rideId, setRideId] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [position, setPosition] = useState({ lat: START_LAT, lon: START_LON });
  const [distanceM, setDistanceM] = useState(0);
  const [pointCount, setPointCount] = useState(0);
  const [lastUpload, setLastUpload] = useState(null);
  const [mapReady, setMapReady] = useState(false);
  const [finishing, setFinishing] = useState(false);
  const [hitKey, setHitKey] = useState(0); // 좌표가 갱신될 때마다 증가 → 타격 플래시 리렌더 트리거
  const [elapsedSec, setElapsedSec] = useState(0);
  const [kmMarkers, setKmMarkers] = useState([]); // 1km마다 지나친 지점 (HUD용 스플릿 표시)
  const [gps, setGps] = useState(() => {
    const s = geolocationStatus();
    return { kind: s.ok ? 'idle' : 'error', message: s.ok ? '위치 확인 중...' : s.message, accuracyM: null };
  });
  const [gpsUsable] = useState(() => geolocationStatus().ok);

  const batchSeqRef = useRef(0);
  const pendingPointsRef = useRef([]);
  const mapElRef = useRef(null);
  const mapRef = useRef(null);
  const fogCanvasRef = useRef(null);
  const visitedRef = useRef([{ lat: START_LAT, lon: START_LON }]);
  const startedAtRef = useRef(Date.now());
  const lastKmRef = useRef(0);
  const lastPointRef = useRef(null);   // 마지막으로 채택한 실제 좌표
  // 더미 이동 전용 가상 시계. Date.now()를 쓰면 빠르게 연타했을 때 같은 밀리초가 찍혀
  // 서버가 중복 표본으로 전부 버립니다(정산이 0으로 나옴).
  const simClockRef = useRef(Date.now() - 3600_000);

  function drawRideFog() {
    const map = mapRef.current;
    const canvasEl = fogCanvasRef.current;
    const containerEl = mapElRef.current;
    if (!map || !canvasEl || !containerEl) return;
    const size = resizeCanvasToContainer(canvasEl, containerEl);
    if (!size) return;
    const { w, h } = size;
    const ctx = canvasEl.getContext('2d');
    paintMistBase(ctx, canvasEl, w, h, { seed: 20260808 });
    visitedRef.current.forEach((p) => {
      const pt = latLonToPixel(map, p.lat, p.lon);
      if (!pt) return;
      punchHole(ctx, pt.x, pt.y, RIDE_REVEAL_RADIUS);
    });
  }

  // 주행 시작 or 이어받기
  // 주행 시작(POST /rides)은 재실행되면 안 되는 부작용이라 ref로 한 번만 실행되게 막음
  // (개발 모드 StrictMode가 effect를 두 번 실행해도 중복 생성되지 않도록)
  // ※ 여기서는 "cancelled" 클로저 변수로 취소하지 않음 — ref 가드가 이미 실행을
  //   한 번으로 막아주는데, StrictMode의 가짜 언마운트에서 cancelled=true가 찍히면
  //   실제 응답이 온 뒤에도 setRideId/setLoading이 영영 스킵되는 버그가 생기기 때문.
  const initStartedRef = useRef(false);
  useEffect(() => {
    if (initStartedRef.current) return;
    initStartedRef.current = true;

    async function init() {
      try {
        const active = await getActiveRide();
        setRideId(active.rideId);
        batchSeqRef.current = active.nextBatchSeq || 0;
      } catch (err) {
        if (err.status !== 404) {
          setError(err.message || '주행 정보를 불러오지 못했어요.');
          setLoading(false);
          return;
        }
        try {
          const ride = await startRide(regionCode, deckCardCodes || []);
          setRideId(ride.rideId);
          batchSeqRef.current = ride.nextBatchSeq || 0;
        } catch (startErr) {
          setError(startErr.message || '주행을 시작하지 못했어요.');
        }
      } finally {
        setLoading(false);
      }
    }

    init();
  }, [regionCode, deckCardCodes]);

  // 카카오맵 로드
  // ※ loading이 true인 동안은 지도 div 자체가 렌더링되지 않아서(스피너만 보여줌) mapElRef.current가
  //   null임 — 그래서 loading을 의존성에 넣어 loading이 false로 바뀐 뒤에도 한 번 더 시도하고,
  //   이미 지도를 만들었으면(mapRef.current) 재생성하지 않도록 가드를 둠.
  useEffect(() => {
    if (mapRef.current) return;
    if (!isVWorldMapConfigured() || !mapElRef.current) return;
    let cancelled = false;
    loadVWorldMap()
      .then(async (vw) => {
        if (cancelled || !mapElRef.current) return;
        const map = createVWorldMap(vw, MAP_EL_ID, {
          centerLat: position.lat,
          centerLon: position.lon,
          height: 500,
        });
        await waitForViewer(map); // 두 번째 이후 지도부터 내부 초기화가 늦게 끝날 수 있음
        if (cancelled) return;
        addImageMarker(map, RIDER_MARKER_ID, {
          lat: position.lat, lon: position.lon, image: riderMarkerImg, width: 48, height: 48,
        });
        addPolyline(map, ROUTE_LINE_ID, [position]);
        mapRef.current = map;
        setMapReady(true);

        onCameraMoveEnd(map, drawRideFog);
        setTimeout(drawRideFog, 600);
      })
      .catch((err) => setError((prev) => prev || err.message));
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading]);

  // 위치가 바뀔 때마다 지도 갱신 + 안개 걷기
  useEffect(() => {
    if (!mapReady) return;
    const map = mapRef.current;
    updateEntityPosition(map, RIDER_MARKER_ID, position.lat, position.lon);
    setCameraCenter(map, position.lat, position.lon);
    setPolylinePositions(map, ROUTE_LINE_ID, visitedRef.current);
    drawRideFog();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [position, mapReady]);

  // 좌표 하나를 화면·버퍼에 반영합니다. 실제 GPS와 더미 이동이 같은 경로를 씁니다.
  function acceptPoint(point) {
    const prev = lastPointRef.current;
    const moved = prev ? distanceMeters(prev.lat, prev.lon, point.lat, point.lon) : 0;

    // PACE는 실제로 페달을 밟기 시작한 시점부터 재야 함 — 등록·덱편성하는 동안의
    // 대기 시간까지 넣으면 첫 이동에서 말도 안 되게 느린 페이스가 찍힘
    if (prev === null) {
      startedAtRef.current = Date.now();
    }
    lastPointRef.current = { lat: point.lat, lon: point.lon };

    pendingPointsRef.current.push(point);
    visitedRef.current.push({ lat: point.lat, lon: point.lon });
    setPointCount((c) => c + 1);
    setPosition({ lat: point.lat, lon: point.lon });
    setHitKey((k) => k + 1);

    setDistanceM((d) => {
      const nd = d + moved;
      const km = Math.floor(nd / 1000);
      if (km > lastKmRef.current) {
        lastKmRef.current = km;
        setKmMarkers((list) => [...list, { km, elapsedSec: (Date.now() - startedAtRef.current) / 1000 }]);
      }
      return nd;
    });
  }

  // 실제 GPS 추적 — 주행이 시작된 뒤에만 켭니다.
  useEffect(() => {
    if (!rideId || !gpsUsable) return;
    const stop = watchRide({
      onPoint: (point) => acceptPoint(point),
      onStatus: (s) => setGps(s),
    });
    return stop;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rideId, gpsUsable]);

  // N초마다 쌓인 좌표를 배치로 업로드
  useEffect(() => {
    if (!rideId) return;
    const timer = setInterval(() => {
      if (pendingPointsRef.current.length === 0) return;
      const points = pendingPointsRef.current;
      pendingPointsRef.current = [];
      const seq = batchSeqRef.current;
      batchSeqRef.current += 1;

      uploadBatch(rideId, {
        batchSeq: seq,
        idempotencyKey: `${rideId}-${seq}`,
        points,
        imuWindows: [],
      })
        .then((res) => setLastUpload(res))
        .catch((err) => setError(err.message || '배치 업로드에 실패했어요.'));
    }, BATCH_INTERVAL_MS);

    return () => clearInterval(timer);
  }, [rideId]);

  // 경과 시간 — HUD의 PACE(분/km) 계산용
  useEffect(() => {
    if (!rideId) return;
    const timer = setInterval(() => {
      setElapsedSec((Date.now() - startedAtRef.current) / 1000);
    }, 1000);
    return () => clearInterval(timer);
  }, [rideId]);

  // GPS를 쓸 수 없는 환경(HTTP 접속 등)에서 데모용으로 쓰는 더미 이동.
  // HUD 갱신은 acceptPoint가 담당하므로 여기서는 좌표만 만들어 넘깁니다.
  function simulateMove() {
    const base = lastPointRef.current ?? { lat: position.lat, lon: position.lon };
    // 가상 시계를 1초씩 전진시킵니다. 실제 시각을 쓰면 연타 시 같은 밀리초가 찍혀
    // 서버 품질 검사가 전부 중복으로 걸러 버립니다.
    simClockRef.current += 1000;
    acceptPoint({
      epochMs: simClockRef.current,
      lat: base.lat + 0.00004,
      lon: base.lon + 0.00004,
      accuracyM: 8.0,
      speedMps: 5.0,
    });
  }

  async function handleFinish() {
    if (!rideId) return;
    setFinishing(true);
    // 남은 버퍼가 있으면 종료 전에 마지막으로 한 번 더 올림
    if (pendingPointsRef.current.length > 0) {
      const points = pendingPointsRef.current;
      pendingPointsRef.current = [];
      const seq = batchSeqRef.current;
      batchSeqRef.current += 1;
      try {
        await uploadBatch(rideId, {
          batchSeq: seq,
          idempotencyKey: `${rideId}-${seq}`,
          points,
          imuWindows: [],
        });
      } catch {
        // 종료 흐름은 막지 않음 — 정산 화면에서 재시도 가능
      }
    }
    onFinish(rideId, distanceM, { points: visitedRef.current, kmMarkers });
  }

  if (loading) {
    return (
      <div className="app-shell">
        <div className="app-content"><div className="spinner" /></div>
      </div>
    );
  }

  return (
    <div className="app-shell">
      <div className="top-bar">
        <button className="icon-btn" onClick={onCancel}><IconChevronLeft /></button>
        <div style={{ fontSize: 13, fontWeight: 700 }}>
          <span className="pulse-dot" />주행 중
        </div>
        <div style={{ width: 40 }} />
      </div>

      <div className="app-content">
        {error && <div className="error-box">{error}</div>}

        <div className="map-frame">
          {isVWorldMapConfigured() ? (
            <>
              <div ref={mapElRef} id={MAP_EL_ID} className="kakao-map-el" />
              <canvas ref={fogCanvasRef} className="fog-canvas" />
              {hitKey > 0 && <div key={hitKey} className="ride-hit-flash" />}
              {mapReady && (
                <div className="ride-hud">
                  <div className="ride-hud-top">
                    <div className="ride-hud-type">RIDE</div>
                  </div>
                  <div className="ride-hud-bottom">
                    <div className="ride-hud-stat">
                      <div className="l">PACE</div>
                      <div className="v">
                        {formatPace(distanceM > 0 ? elapsedSec / (distanceM / 1000) : 0)}
                        <span className="u">/km</span>
                      </div>
                    </div>
                    <div className="ride-hud-stat">
                      <div className="l">DISTANCE</div>
                      <div className="v">{(distanceM / 1000).toFixed(2)}<span className="u">km</span></div>
                    </div>
                  </div>
                </div>
              )}
            </>
          ) : (
            <div className="map-fallback">
              <IconMap style={{ fontSize: 28 }} />
              <div>
                VWorld API 키가 설정되지 않았어요.<br />
                <code>frontend/.env</code>에 <code>VITE_VWORLD_MAP_KEY</code>를 넣으면 지도가 표시됩니다.
              </div>
              <div style={{ marginTop: 8, color: 'var(--accent)' }}>
                현재 좌표: {position.lat.toFixed(5)}, {position.lon.toFixed(5)}
              </div>
              <div style={{ marginTop: 4, fontSize: 12, opacity: 0.75 }}>{gps.message}</div>
            </div>
          )}
        </div>

        <div className="ride-stats">
          <div className="stat-tile">
            <div className="v">{Math.round(distanceM)}m</div>
            <div className="l">이동 거리</div>
          </div>
          <div className="stat-tile">
            <div className="v">{pointCount}</div>
            <div className="l">기록된 좌표</div>
          </div>
          <div className="stat-tile">
            <div className="v">{lastUpload ? '✓' : '…'}</div>
            <div className="l">{lastUpload ? '마지막 업로드 완료' : `${BATCH_INTERVAL_MS / 1000}초마다 업로드`}</div>
          </div>
        </div>

        <div className={`gps-status gps-${gps.kind}`}>
          <span className="dot" /> {gps.message}
          {gps.accuracyM != null && <span className="acc">±{Math.round(gps.accuracyM)}m</span>}
        </div>

        {/* 실제 GPS를 쓸 수 있어도 항상 보여줌 — 실내 촬영·시연처럼 실제로 움직일 수 없을 때
            쓰는 보조 버튼. GPS가 정상 수신되면 그쪽 좌표가 우선이고, 이 버튼은 그냥 여분임. */}
        <button className="btn-secondary" onClick={simulateMove} style={{ marginBottom: 10, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
          <IconPin /> {gpsUsable ? '수동으로 이동 (실내·시연용)' : '더미 GPS로 이동 (데모용)'}
        </button>

        <div className="note-box">
          {gpsUsable
            ? '실제 GPS로 기록 중이에요. 실내라 위치가 안 움직인다면 위 버튼으로 수동 이동할 수 있어요. 브라우저 특성상 화면을 끄거나 다른 앱으로 넘어가면 기록이 멈춥니다.'
            : 'HTTPS가 아니어서 실제 GPS를 쓸 수 없어요. 데모용 더미 이동 버튼으로 대체합니다.'}
        </div>
      </div>

      <div style={{ padding: '0 20px 24px' }}>
        <button className="btn-primary" onClick={handleFinish} disabled={!rideId || finishing}>
          {finishing ? '정산 중...' : '주행 종료하고 정산하기'}
        </button>
      </div>
    </div>
  );
}
