// VWorld 3D 지도(WebGL API 3.0) 로더.
// 카카오맵과 마찬가지로 .env에 VITE_VWORLD_MAP_KEY=발급받은_인증키 를 넣으면 자동으로 3D 지도가 뜸.
// 내부적으로 Cesium을 그대로 쓰기 때문에, vw.Map으로 초기화한 뒤 세부 기능(마커·좌표 변환·이동 감지)은
// 필요하면 Cesium 표준 API로 내려가서 씀 (window.Cesium 도 함께 로드됨).
//
// 참고: https://www.vworld.kr/dev/v4dv_opnws3dmap3guide_s001.do
// script 태그: https://map.vworld.kr/js/webglMapInit.js.do?version=3.0&apiKey=[인증키]

let loadPromise = null;

export function isVWorldMapConfigured() {
  return Boolean(import.meta.env.VITE_VWORLD_MAP_KEY);
}

// VWorld SDK <script> 태그는 index.html의 <head>에 정적으로 박혀 있음(문서 파싱 중에
// document.write로 의존 스크립트를 불러와야 해서 동적 주입이 불가능함, vworldMap.js 상단 주석 참고).
// 여기서는 그게 다 로드되기를 기다리기만 함.
export function loadVWorldMap() {
  if (!isVWorldMapConfigured()) {
    return Promise.reject(new Error('VITE_VWORLD_MAP_KEY가 설정되지 않았습니다.'));
  }

  if (window.vw?.Map) {
    return Promise.resolve(window.vw);
  }

  if (loadPromise) {
    return loadPromise;
  }

  loadPromise = new Promise((resolve, reject) => {
    const startedAt = Date.now();
    const check = () => {
      if (window.vw?.Map) {
        resolve(window.vw);
      } else if (Date.now() - startedAt > 15000) {
        reject(new Error('VWorld SDK 로드를 기다리다 시간 초과했습니다.'));
      } else {
        setTimeout(check, 100);
      }
    };
    check();
  });

  return loadPromise;
}

// vw.Map을 지정한 div(mapId)에 초기화하고 인스턴스를 돌려줌.
// centerLat/centerLon: 초기 카메라 중심, height: 카메라 고도(m, 클수록 더 넓게 보임)
export function createVWorldMap(vw, mapId, { centerLat, centerLon, height = 1200 } = {}) {
  const map = new vw.Map();
  map.setOption({
    mapId,
    initPosition: new vw.CameraPosition(
      new vw.CoordZ(centerLon, centerLat, height),
      new vw.Direction(0, -90, 0),
    ),
    logo: true,
    navigation: true,
  });
  try {
    map.start();
  } catch (e) {
    // SDK가 매 map.start()마다 window.viewer를 전역으로 재정의하려 시도하는데,
    // 화면을 옮겨다니며 두 번째 지도를 만들면 이미 정의된 속성이라 던짐.
    // 우리는 window.viewer가 아니라 map._wsViewer를 직접 쓰므로 무해함 — 무시하고 계속함.
    if (!/redefine property.*viewer/i.test(e.message || '')) throw e;
  }
  return map;
}

// vw.Map 내부의 실제 Cesium.Viewer 인스턴스.
// vw의 공개 메서드(createMarker 등)는 문서화가 안 돼있고 난독화돼서 신뢰하기 어려워서,
// 마커/이벤트/좌표 변환은 여기서 표준 Cesium API로 직접 처리함.
export function getViewer(map) {
  return map._wsViewer;
}

// map.start() 호출이 끝나도 내부 Cesium.Viewer(_wsViewer)가 그 자리에서 바로 준비돼
// 있다는 보장이 없음 — 페이지에서 두 번째, 세 번째로 만드는 지도일수록 내부 초기화가
// 살짝 늦게 끝나는 경우가 있어서, 그 직후 addPolyline/addDotMarker를 부르면
// "Cannot read properties of undefined (reading 'entities')"로 죽을 수 있음.
// 그래서 실제로 쓰기 전에 짧게 폴링하며 기다림.
export function waitForViewer(map, timeoutMs = 5000) {
  if (map._wsViewer) return Promise.resolve(map._wsViewer);
  return new Promise((resolve, reject) => {
    const startedAt = Date.now();
    const check = () => {
      if (map._wsViewer) {
        resolve(map._wsViewer);
      } else if (Date.now() - startedAt > timeoutMs) {
        reject(new Error('지도 뷰어 초기화를 기다리다 시간 초과했습니다.'));
      } else {
        setTimeout(check, 50);
      }
    };
    check();
  });
}

// 위도/경도(+고도)를 현재 화면의 픽셀 좌표로 변환. 카메라 뒤쪽/화면 밖이면 null.
// fog-of-war의 punchHole(x, y, radius)에 그대로 넘길 수 있음.
export function latLonToPixel(map, lat, lon, height = 0) {
  const cartesian = window.Cesium.Cartesian3.fromDegrees(lon, lat, height);
  const px = map.coordToPixel(cartesian);
  if (!px) return null;
  return { x: px.x, y: px.y };
}

// 점 마커(셀 상태 등 단색 원) 추가. 반환된 id로 removeMarker에서 지울 수 있음.
export function addDotMarker(map, id, { lat, lon, height = 0, color = '#ffffff', pixelSize = 12 }) {
  const viewer = getViewer(map);
  const Cesium = window.Cesium;
  return viewer.entities.add({
    id,
    position: Cesium.Cartesian3.fromDegrees(lon, lat, height),
    point: {
      pixelSize,
      color: Cesium.Color.fromCssColorString(color),
      outlineColor: Cesium.Color.fromCssColorString(color).withAlpha(0.5),
      outlineWidth: 4,
      disableDepthTestDistance: Number.POSITIVE_INFINITY,
    },
  });
}

// 이미지(PNG 등)를 쓰는 빌보드 마커 추가 (라이더 아이콘 등).
export function addImageMarker(map, id, { lat, lon, height = 0, image, width = 40, height: h = 40 }) {
  const viewer = getViewer(map);
  const Cesium = window.Cesium;
  return viewer.entities.add({
    id,
    position: Cesium.Cartesian3.fromDegrees(lon, lat, height),
    billboard: {
      image,
      width,
      height: h,
      disableDepthTestDistance: Number.POSITIVE_INFINITY,
    },
  });
}

// 마커 위치 갱신 (라이더처럼 계속 움직이는 마커용)
export function updateEntityPosition(map, id, lat, lon, height = 0) {
  const viewer = getViewer(map);
  const entity = viewer.entities.getById(id);
  if (!entity) return;
  entity.position = window.Cesium.Cartesian3.fromDegrees(lon, lat, height);
}

// 카메라를 애니메이션 없이 즉시 특정 위경도 중심으로 이동 (라이더 추적용)
export function setCameraCenter(map, lat, lon, height) {
  const viewer = getViewer(map);
  const Cesium = window.Cesium;
  const dest = height != null
    ? Cesium.Cartesian3.fromDegrees(lon, lat, height)
    : (() => {
      // 고도를 안 주면 현재 카메라 고도를 유지한 채 위/경도만 옮김
      const carto = Cesium.Cartographic.fromCartesian(viewer.camera.position);
      return Cesium.Cartesian3.fromRadians(
        Cesium.Math.toRadians(lon),
        Cesium.Math.toRadians(lat),
        carto.height,
      );
    })();
  viewer.camera.setView({
    destination: dest,
    orientation: { heading: viewer.camera.heading, pitch: viewer.camera.pitch, roll: 0 },
  });
}

// 이동 경로(폴리라인) 추가. id로 이후 setPolylinePositions에서 갱신 가능.
export function addPolyline(map, id, points, { color = '#c6f135', width = 4 } = {}) {
  const viewer = getViewer(map);
  const Cesium = window.Cesium;
  const flat = points.flatMap((p) => [p.lon, p.lat, 0]);
  return viewer.entities.add({
    id,
    polyline: {
      positions: Cesium.Cartesian3.fromDegreesArrayHeights(flat),
      width,
      material: Cesium.Color.fromCssColorString(color),
      clampToGround: true,
    },
  });
}

export function setPolylinePositions(map, id, points) {
  const viewer = getViewer(map);
  const Cesium = window.Cesium;
  const entity = viewer.entities.getById(id);
  if (!entity) return;
  const flat = points.flatMap((p) => [p.lon, p.lat, 0]);
  entity.polyline.positions = Cesium.Cartesian3.fromDegreesArrayHeights(flat);
}

export function removeMarker(map, id) {
  getViewer(map).entities.removeById(id);
}

export function removeAllMarkers(map, exceptIds = []) {
  const viewer = getViewer(map);
  const keep = new Set(exceptIds);
  [...viewer.entities.values].forEach((e) => {
    if (!keep.has(e.id)) viewer.entities.removeById(e.id);
  });
}

// 카메라 이동(드래그/줌)이 끝났을 때 콜백. 카카오맵의 'idle' 이벤트와 같은 역할.
// 반환값은 해제 함수.
export function onCameraMoveEnd(map, callback) {
  const viewer = getViewer(map);
  viewer.camera.moveEnd.addEventListener(callback);
  return () => viewer.camera.moveEnd.removeEventListener(callback);
}

// 현재 화면에 보이는 영역을 위경도 bbox로 반환 (지형이 없으면 null일 수 있음)
export function getViewBounds(map) {
  const viewer = getViewer(map);
  const Cesium = window.Cesium;
  const rect = viewer.camera.computeViewRectangle(viewer.scene.globe.ellipsoid);
  if (!rect) return null;
  return {
    minLon: Cesium.Math.toDegrees(rect.west),
    minLat: Cesium.Math.toDegrees(rect.south),
    maxLon: Cesium.Math.toDegrees(rect.east),
    maxLat: Cesium.Math.toDegrees(rect.north),
  };
}
