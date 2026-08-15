// 카카오맵 JavaScript SDK 로더
// .env 파일에 VITE_KAKAO_MAP_KEY=발급받은_JS_키 를 넣으면 자동으로 지도가 뜸.
// 키가 없으면 로드를 시도하지 않고 null을 반환 (화면은 fallback UI를 보여주면 됨).

let loadPromise = null;

export function isKakaoMapConfigured() {
  return Boolean(import.meta.env.VITE_KAKAO_MAP_KEY);
}

export function loadKakaoMaps() {
  if (!isKakaoMapConfigured()) {
    return Promise.reject(new Error('VITE_KAKAO_MAP_KEY가 설정되지 않았습니다.'));
  }

  if (window.kakao?.maps) {
    return Promise.resolve(window.kakao);
  }

  if (loadPromise) {
    return loadPromise;
  }

  loadPromise = new Promise((resolve, reject) => {
    const key = import.meta.env.VITE_KAKAO_MAP_KEY;
    const script = document.createElement('script');
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${key}&autoload=false`;
    script.async = true;
    script.onload = () => {
      window.kakao.maps.load(() => resolve(window.kakao));
    };
    script.onerror = () => reject(new Error('카카오맵 SDK 로드에 실패했습니다.'));
    document.head.appendChild(script);
  });

  return loadPromise;
}
