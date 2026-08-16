// 화면: 지도 (덱 편성 다음에 이 화면으로 옴)
// 원래 VWorld 3D였는데, 네비게이터(iOS)와 같은 Google Maps(2D)로 교체함.
// 주행 데이터 기반으로 서버가 계산한 결함 후보(레드포인트, /map/anomalies)를 마커로 찍고,
// 하나를 탭하면 그 지점의 몬스터와 배틀하는 화면(MonsterBattle)으로 넘어감.
//
// ⚠️ 지금은 지도 중심을 고정 좌표로 두고 그 주변만 조회함. 나중에 지도를 움직일 때마다
//    다시 조회(bbox 갱신)하는 기능을 넣으려면 map.addListener('idle', ...)에서
//    getBounds()로 다시 fetchAnomalies 하면 됨.

import { useEffect, useRef, useState } from 'react';
import { getMapAnomalies } from '../api';
import { loadGoogleMap, isGoogleMapConfigured, createGoogleMap, renderAnomalyMarkers } from '../googleMap';
import BottomNav from '../components/BottomNav';
import { IconChevronLeft, IconMap } from '../components/Icons';

const MAX_SPAN = 0.045; // 서버 제한(0.05도)보다 살짝 여유를 둠
const DEFAULT_CENTER = { lat: 37.23, lon: 127.12 };
const MAP_EL_ID = 'r2d-map-view-google';

export default function MapView({ onBack, onNavigate, onSelectAnomaly }) {
  const mapElRef = useRef(null);
  const mapRef = useRef(null);
  const mapsRef = useRef(null);
  const markersRef = useRef([]);
  const [anomalies, setAnomalies] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  function fetchAnomalies() {
    const half = MAX_SPAN / 2;
    setLoading(true);
    setError(null);
    getMapAnomalies({
      minLat: DEFAULT_CENTER.lat - half,
      maxLat: DEFAULT_CENTER.lat + half,
      minLon: DEFAULT_CENTER.lon - half,
      maxLon: DEFAULT_CENTER.lon + half,
    })
      .then(setAnomalies)
      .catch((err) => setError(err.message || '지도 데이터를 불러오지 못했어요.'))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    fetchAnomalies();
  }, []);

  // 구글맵 로드
  useEffect(() => {
    if (!isGoogleMapConfigured() || !mapElRef.current) return;
    let cancelled = false;
    loadGoogleMap()
      .then((maps) => {
        if (cancelled || !mapElRef.current) return;
        mapsRef.current = maps;
        mapRef.current = createGoogleMap(maps, MAP_EL_ID, {
          centerLat: DEFAULT_CENTER.lat,
          centerLon: DEFAULT_CENTER.lon,
        });
      })
      .catch((err) => setError((prev) => prev || err.message));
    return () => {
      cancelled = true;
    };
  }, []);

  // anomalies가 바뀔 때마다 마커 다시 그림
  useEffect(() => {
    if (!mapRef.current || !mapsRef.current) return;
    markersRef.current = renderAnomalyMarkers(
      mapsRef.current,
      mapRef.current,
      anomalies,
      markersRef.current,
      (anomaly) => onSelectAnomaly?.(anomaly),
    );
  }, [anomalies]);

  const confirmedCount = anomalies.filter((a) => a.state === '확정').length;

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
          {isGoogleMapConfigured() ? (
            <div ref={mapElRef} id={MAP_EL_ID} className="kakao-map-el" style={{ width: '100%', height: '100%' }} />
          ) : (
            <div className="map-fallback">
              <IconMap style={{ fontSize: 28 }} />
              <div>
                Google Maps API 키가 설정되지 않았어요.<br />
                <code>frontend/.env</code>에 <code>VITE_GOOGLE_MAPS_KEY</code>를 넣으면 지도가 표시됩니다.
                아래는 목록으로 보는 결함 후보예요.
              </div>
            </div>
          )}
        </div>

        <p className="hint" style={{ marginTop: 8 }}>
          빨간 점을 탭하면 그 지점의 몬스터와 싸울 수 있어요. (진하게 빨간 점 {confirmedCount}개는 확정된 결함이에요)
        </p>

        {loading && <div className="spinner" />}

        {!isGoogleMapConfigured() && !loading && (
          <>
            <div className="section-title">결함 후보 ({anomalies.length})</div>
            {anomalies.map((a) => (
              <button
                key={a.cellId}
                className="tap-card card"
                style={{ marginTop: 8 }}
                onClick={() => onSelectAnomaly?.(a)}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>{a.anomalyClass}</span>
                  <span className={a.state === '확정' ? 'badge type-BOSS' : 'badge'}>{a.state}</span>
                </div>
                <div className="note" style={{ marginTop: 4 }}>{a.note}</div>
              </button>
            ))}
          </>
        )}
      </div>
      <BottomNav screen="map" onNavigate={onNavigate} />
    </div>
  );
}
