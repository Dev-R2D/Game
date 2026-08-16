// Google Maps JS 로더 (2D 지도).
// 네비게이터(iOS)가 쓰는 지도와 같은 Google Maps라서, 여기서도 구글맵을 씀.
// .env에 VITE_GOOGLE_MAPS_KEY=발급받은_웹용_API_키 를 넣으면 지도가 뜸.
// ※ iOS 네이티브 SDK 키와는 별개의 키가 필요함 — "Maps JavaScript API"가 활성화된 웹용 키.
//
// 참고: https://developers.google.com/maps/documentation/javascript/overview

let loadPromise = null;

export function isGoogleMapConfigured() {
  return Boolean(import.meta.env.VITE_GOOGLE_MAPS_KEY);
}

export function loadGoogleMap() {
  if (!isGoogleMapConfigured()) {
    return Promise.reject(new Error('VITE_GOOGLE_MAPS_KEY가 설정되지 않았습니다.'));
  }
  if (window.google?.maps) {
    return Promise.resolve(window.google.maps);
  }
  if (loadPromise) {
    return loadPromise;
  }

  loadPromise = new Promise((resolve, reject) => {
    const key = import.meta.env.VITE_GOOGLE_MAPS_KEY;
    const script = document.createElement('script');
    script.src = `https://maps.googleapis.com/maps/api/js?key=${key}&loading=async`;
    script.async = true;
    script.onerror = () => reject(new Error('Google Maps SDK 로드에 실패했습니다.'));
    script.onload = () => {
      if (window.google?.maps) resolve(window.google.maps);
      else reject(new Error('Google Maps SDK가 로드됐지만 google.maps를 찾을 수 없습니다.'));
    };
    document.head.appendChild(script);
  });

  return loadPromise;
}

// containerEl 안에 지도를 하나 만들어서 { map } 형태로 돌려줌.
export function createGoogleMap(maps, elId, { centerLat, centerLon, zoom = 15 } = {}) {
  const el = document.getElementById(elId);
  const map = new maps.Map(el, {
    center: { lat: centerLat, lng: centerLon },
    zoom,
    disableDefaultUI: true,
    zoomControl: true,
    styles: DARK_MAP_STYLE, // 앱이 다크 테마라서 지도도 다크로
  });
  return map;
}

// 결함 후보(레드포인트)를 마커로 찍고, 클릭하면 onPick(anomaly)를 호출함.
// 이전에 그렸던 마커는 지우고 새로 그림 — 반환값(marker 배열)을 다음 호출 때 넘기면 됨.
export function renderAnomalyMarkers(maps, map, anomalies, prevMarkers, onPick) {
  (prevMarkers || []).forEach((m) => m.setMap(null));

  return (anomalies || []).map((a) => {
    const confirmed = a.state === '확정';
    const marker = new maps.Marker({
      map,
      position: { lat: a.lat, lng: a.lon },
      title: a.anomalyClass || '결함 후보',
      icon: {
        path: maps.SymbolPath.CIRCLE,
        scale: confirmed ? 10 : 7,
        fillColor: confirmed ? '#ff5c5c' : '#8b9280',
        fillOpacity: 0.95,
        strokeColor: '#0a0c08',
        strokeWeight: 2,
      },
    });
    marker.addListener('click', () => onPick(a));
    return marker;
  });
}

// 야간 도로 주행 컨셉(index.css --bg 계열)에 맞춘 다크 지도 스타일
const DARK_MAP_STYLE = [
  { elementType: 'geometry', stylers: [{ color: '#12150f' }] },
  { elementType: 'labels.text.fill', stylers: [{ color: '#8b9280' }] },
  { elementType: 'labels.text.stroke', stylers: [{ color: '#0a0c08' }] },
  { featureType: 'road', elementType: 'geometry', stylers: [{ color: '#2a2f22' }] },
  { featureType: 'water', elementType: 'geometry', stylers: [{ color: '#0d1712' }] },
  { featureType: 'poi', stylers: [{ visibility: 'off' }] },
];
