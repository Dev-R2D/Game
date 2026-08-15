// 화면 ⑦: 지도
// VWorld 3D 지도 위에 셀 상태(/map/cells)와 이상 후보(/map/anomalies)를 마커로 표시함.
// 지도를 움직일 때마다 "현재 보이는 영역"을 bbox로 계산해서 다시 조회함(한 변 0.05도 제한).
// 포켓몬고 느낌으로, 아직 조사되지 않은("미조사") 지역은 안개로 덮고
// 조사된 셀 주변만 캔버스로 부드럽게 걷어내는 fog-of-war를 얹음.

import { useEffect, useRef, useState } from 'react';
import { getMapCells, getMapAnomalies } from '../api';
import { loadVWorldMap, isVWorldMapConfigured, createVWorldMap, addDotMarker, removeAllMarkers, onCameraMoveEnd, getViewBounds, latLonToPixel } from '../vworldMap';
import { paintMistBase, punchHole, resizeCanvasToContainer } from '../fog';
import BottomNav from '../components/BottomNav';
import { IconChevronLeft, IconMap } from '../components/Icons';

const MAX_SPAN = 0.045; // 서버 제한(0.05도)보다 살짝 여유를 둠
const DEFAULT_CENTER = { lat: 37.23, lon: 127.12 };
const FOG_REVEAL_RADIUS = 60; // px, 조사된 셀 하나가 걷어내는 안개 반경
const MAP_EL_ID = 'r2d-map-view-vworld';

const CELL_COLORS = {
  '미조사': '#8b9280',
  '갱신 필요': '#7fc4ff',
  '낮은 신뢰도': '#ffd27f',
  '보수 후 재확인': '#ff9f7f',
  '최근 조사됨': '#c6f135',
};

function clampBounds(bounds) {
  const latSpan = Math.min(bounds.maxLat - bounds.minLat, MAX_SPAN);
  const lonSpan = Math.min(bounds.maxLon - bounds.minLon, MAX_SPAN);
  const centerLat = (bounds.maxLat + bounds.minLat) / 2;
  const centerLon = (bounds.maxLon + bounds.minLon) / 2;
  return {
    minLat: centerLat - latSpan / 2,
    maxLat: centerLat + latSpan / 2,
    minLon: centerLon - lonSpan / 2,
    maxLon: centerLon + lonSpan / 2,
  };
}

// map/canvas/cell 목록을 인자로 받아서 매번 최신 값으로 다시 그림
// (React state 클로저에 갇히지 않도록 이벤트 핸들러에서도 이 함수를 그대로 재사용)
function drawFog(map, canvasEl, containerEl, cellList) {
  if (!map || !canvasEl || !containerEl) return;
  const size = resizeCanvasToContainer(canvasEl, containerEl);
  if (!size) return;
  const { w, h } = size;
  const ctx = canvasEl.getContext('2d');

  paintMistBase(ctx, canvasEl, w, h);

  (cellList || [])
    .filter((c) => c.state !== '미조사')
    .forEach((cell) => {
      const pt = latLonToPixel(map, cell.lat, cell.lon);
      if (!pt) return;
      punchHole(ctx, pt.x, pt.y, FOG_REVEAL_RADIUS);
    });
}

export default function MapView({ onBack, onNavigate }) {
  const mapElRef = useRef(null);
  const mapRef = useRef(null);
  const canvasRef = useRef(null);
  const cellsRef = useRef([]);
  const [mapReady, setMapReady] = useState(false);
  const [cells, setCells] = useState([]);
  const [anomalies, setAnomalies] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  function fetchForBounds(bounds) {
    setLoading(true);
    setError(null);
    Promise.all([getMapCells(bounds), getMapAnomalies(bounds)])
      .then(([cellData, anomalyData]) => {
        setCells(cellData);
        setAnomalies(anomalyData);
      })
      .catch((err) => setError(err.message || '지도 데이터를 불러오지 못했어요.'))
      .finally(() => setLoading(false));
  }

  // VWorld 지도 로드 + 카메라 이동 종료 시 bbox 재조회
  useEffect(() => {
    if (!isVWorldMapConfigured() || !mapElRef.current) {
      // 지도 키가 없으면 기본 좌표 주변을 한 번만 조회해서 리스트로 보여줌
      const half = MAX_SPAN / 2;
      fetchForBounds({
        minLat: DEFAULT_CENTER.lat - half,
        maxLat: DEFAULT_CENTER.lat + half,
        minLon: DEFAULT_CENTER.lon - half,
        maxLon: DEFAULT_CENTER.lon + half,
      });
      return;
    }

    let cancelled = false;
    loadVWorldMap().then((vw) => {
      if (cancelled || !mapElRef.current) return;
      const map = createVWorldMap(vw, MAP_EL_ID, {
        centerLat: DEFAULT_CENTER.lat,
        centerLon: DEFAULT_CENTER.lon,
        height: 1200,
      });
      mapRef.current = map;
      setMapReady(true);

      function handleMoveEnd() {
        // 지도 시작 직후엔 지형이 아직 안 잡혀 카메라 rectangle 계산이 실패할 수 있음
        try {
          const bounds = getViewBounds(map);
          if (bounds) fetchForBounds(clampBounds(bounds));
          drawFog(map, canvasRef.current, mapElRef.current, cellsRef.current);
        } catch {
          // 다음 moveEnd나 아래 setTimeout 재시도에서 다시 그려짐
        }
      }

      onCameraMoveEnd(map, handleMoveEnd);
      // 초기 로드 직후 지형이 아직 안 잡혀 bounds가 null일 수 있어 살짝 지연 후 1회 재시도
      handleMoveEnd();
      setTimeout(handleMoveEnd, 600);
    }).catch((err) => setError(err.message));

    return () => {
      cancelled = true;
    };
  }, []);

  // 마커 그리기 + 안개 다시 그리기
  useEffect(() => {
    cellsRef.current = cells;
    if (!mapReady || !mapRef.current) return;
    const map = mapRef.current;
    removeAllMarkers(map);

    cells.forEach((cell) => {
      const color = CELL_COLORS[cell.state] || '#8b9280';
      addDotMarker(map, `cell-${cell.cellId}`, { lat: cell.lat, lon: cell.lon, color, pixelSize: 12 });
    });

    anomalies.forEach((a) => {
      const confirmed = a.state === '확정';
      addDotMarker(map, `anomaly-${a.cellId}`, {
        lat: a.lat,
        lon: a.lon,
        color: confirmed ? '#ff5c5c' : '#8b9280',
        pixelSize: confirmed ? 20 : 16,
      });
    });

    drawFog(map, canvasRef.current, mapElRef.current, cells);
  }, [cells, anomalies, mapReady]);

  return (
    <div className="app-shell">
      <div className="top-bar">
        <button className="icon-btn" onClick={onBack}><IconChevronLeft /></button>
        <div style={{ fontSize: 13, fontWeight: 700 }}>지도</div>
        <div style={{ width: 40 }} />
      </div>

      <div className="app-content">
        {error && <div className="error-box">{error}</div>}

        <div className="map-frame" style={{ height: 420 }}>
          {isVWorldMapConfigured() ? (
            <>
              <div ref={mapElRef} id={MAP_EL_ID} className="kakao-map-el" />
              <canvas ref={canvasRef} className="fog-canvas" />
            </>
          ) : (
            <div className="map-fallback">
              <IconMap style={{ fontSize: 28 }} />
              <div>
                VWorld API 키가 설정되지 않았어요.<br />
                <code>frontend/.env</code>에 <code>VITE_VWORLD_MAP_KEY</code>를 넣으면 지도가 표시됩니다.
                아래는 기본 좌표 주변 데이터 목록이에요.
              </div>
            </div>
          )}
        </div>

        <div className="section-title">
          범례
        </div>
        <div className="card" style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
          {Object.entries(CELL_COLORS).map(([state, color]) => (
            <span key={state} style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11, color: 'var(--text-dim)' }}>
              <span style={{ width: 8, height: 8, borderRadius: '50%', background: color, display: 'inline-block' }} />
              {state}
            </span>
          ))}
        </div>
        {isVWorldMapConfigured() && (
          <p className="hint" style={{ marginTop: 8 }}>
            안개가 낀 곳은 아직 아무도 달리지 않은 미조사 구간이에요. 라이딩으로 걷어내 보세요.
          </p>
        )}

        {loading && <div className="spinner" />}

        {!isVWorldMapConfigured() && !loading && (
          <>
            <div className="section-title">이상 후보 ({anomalies.length})</div>
            {anomalies.map((a) => (
              <div className="card" key={a.cellId}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>{a.anomalyClass}</span>
                  <span className={a.state === '확정' ? 'badge type-BOSS' : 'badge'}>{a.state}</span>
                </div>
                <div className="note" style={{ marginTop: 4 }}>{a.note}</div>
              </div>
            ))}
          </>
        )}
      </div>
      <BottomNav screen="map" onNavigate={onNavigate} />
    </div>
  );
}
