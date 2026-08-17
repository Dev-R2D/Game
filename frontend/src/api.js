// R2D 백엔드와 통신하는 부분을 한 곳에 모아둔 파일
// 화면(컴포넌트)에서는 이 파일의 함수만 불러다 쓰면 됨

// 로컬 개발: vite.config.js의 proxy를 타는 상대경로.
// 배포 환경: VITE_API_BASE_URL(예: https://r2d-backend.onrender.com/api/v1)을 설정하면 그쪽으로 바로 붙음.
const BASE_URL = import.meta.env.VITE_API_BASE_URL || './api/v1';

// publicId는 로그인 대신 쓰는 신분증 같은 것. 브라우저에 저장해두고 계속 재사용함.
export function getPlayerId() {
  return localStorage.getItem('r2d_player_id');
}

export function setPlayerId(id) {
  localStorage.setItem('r2d_player_id', id);
}

// 실제 fetch 호출을 감싸는 공통 함수
// path: '/players' 처럼 BASE_URL 뒤에 붙는 부분
// method: 'GET' | 'POST' | 'PATCH' 등
// body: 요청 본문 (JS 객체로 넘기면 알아서 JSON으로 변환)
async function request(path, { method = 'GET', body } = {}) {
  const playerId = getPlayerId();

  const res = await fetch(`${BASE_URL}${path}`, {
    method,
    headers: {
      ...(body && { 'Content-Type': 'application/json' }),
      ...(playerId && { 'X-Player-Id': playerId }),
    },
    body: body ? JSON.stringify(body) : undefined,
  });

  const data = await res.json().catch(() => null);

  if (!res.ok) {
    // 서버 오류 형식: { code, message, timestamp }
    const err = new Error(data?.message || '알 수 없는 오류가 발생했습니다.');
    err.code = data?.code;
    err.status = res.status;
    throw err;
  }

  return data;
}

// ── 화면 1: 플레이어 등록 ──────────────────────────────
export function registerPlayer(nickname) {
  return request('/players', { method: 'POST', body: { nickname } });
}

export function getMyProfile() {
  return request('/players/me');
}

export function getMyCards() {
  return request('/players/me/cards');
}

// ── 화면 2: 미션 / 보스 / 덱 ──────────────────────────
export function getRegions() {
  return request('/regions');
}

export function getMissions(regionCode) {
  return request(`/regions/${regionCode}/missions`);
}

export function getBoss(regionCode) {
  return request(`/regions/${regionCode}/boss`);
}

// 처치된 보스는 /regions/{code}/boss에 안 잡힘(새 보스가 바로 생성되므로) — bossId로 직접 조회
export function getBossById(bossId) {
  return request(`/bosses/${bossId}`);
}

// ── 보스 처치·단계 돌파 보상 (막타 보너스 아님 — 누적 기여 비율로 배분) ──
export function getPendingBossRewards() {
  return request('/boss-rewards/pending');
}

export function claimAllBossRewards() {
  return request('/boss-rewards/claim-all', { method: 'POST' });
}

export function getCardCatalog() {
  return request('/cards');
}

export function previewDeck(cardCodes) {
  return request('/decks/preview', { method: 'POST', body: { cardCodes } });
}

// ── 화면 3~4: 주행 ─────────────────────────────────────
export function startRide(regionCode, deckCardCodes) {
  return request('/rides', { method: 'POST', body: { regionCode, deckCardCodes } });
}

export function uploadBatch(rideId, batch) {
  return request(`/rides/${rideId}/batches`, { method: 'POST', body: batch });
}

export function getActiveRide() {
  return request('/rides/active');
}

// ── 화면 5: 정산 결과 ──────────────────────────────────
export function finishRide(rideId, clientEstimatedDamage) {
  return request(`/rides/${rideId}/finish`, {
    method: 'POST',
    body: clientEstimatedDamage ? { clientEstimatedDamage } : undefined,
  });
}

export function getSettlement(rideId) {
  return request(`/rides/${rideId}/settlement`);
}

// ── 화면 6: 팩 ─────────────────────────────────────────
export function getPendingPacks() {
  return request('/packs/pending');
}

export function openPack(packId) {
  return request(`/packs/${packId}/open`, { method: 'POST' });
}

export function getPackOdds() {
  return request('/packs/odds');
}

// ── 화면 7: 지도 ───────────────────────────────────────
export function getMapCells(bounds) {
  const { minLat, maxLat, minLon, maxLon } = bounds;
  return request(`/map/cells?minLat=${minLat}&maxLat=${maxLat}&minLon=${minLon}&maxLon=${maxLon}`);
}

export function getMapAnomalies(bounds) {
  const { minLat, maxLat, minLon, maxLon } = bounds;
  return request(`/map/anomalies?minLat=${minLat}&maxLat=${maxLat}&minLon=${minLon}&maxLon=${maxLon}`);
}

// ── 몬스터 배틀 ──────────────────────────────────────────
// 덱 편성(3장)을 같이 보내면 시너지 배율이 보상에 반영됨. 안 보내면 중립 덱(배율 1.0).
export function battleAnomaly(cellId, deckCardCodes) {
  return request(`/anomalies/${cellId}/battle`, { method: 'POST', body: { deckCardCodes: deckCardCodes || [] } });
}

// ── 심사 모드 (데모용) ──────────────────────────────────
export function getJudgeLogs() {
  return request('/judge/logs');
}

export function replayJudgeLog(logId, deckCardCodes, playbackSpeed) {
  return request('/judge/replay', { method: 'POST', body: { logId, deckCardCodes, playbackSpeed } });
}
