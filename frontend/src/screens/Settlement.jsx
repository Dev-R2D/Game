// 화면 ⑤~⑦: 정산 결과 / 데미지 임팩트
// POST /rides/{rideId}/finish 를 호출하고, README의 "연출 매핑" 표대로 화면을 구성함.
// 재호출해도 안전한 API라서, 실패 시 재시도 버튼을 그냥 다시 호출하면 됨.

import { useEffect, useRef, useState } from 'react';
import { finishRide, getBossById, claimAllBossRewards } from '../api';
import bossDefaultImg from '../assets/boss-default.png';
import riderMarkerImg from '../assets/rider-marker.png';

function formatPace(secPerKm) {
  if (!Number.isFinite(secPerKm) || secPerKm <= 0) return '–:––';
  const m = Math.floor(secPerKm / 60);
  const s = Math.round(secPerKm % 60);
  return `${m}:${String(s).padStart(2, '0')}`;
}

// 방금 달린 구간별 페이스. VWorld 지도로 실제 경로까지 그려주는 건 페이지에 이미 떠있는
// 다른 3D 지도 인스턴스와 충돌해서(Cesium 렌더 루프가 죽음) 뺐고, 텍스트 스플릿만 보여줌.
function RouteSplits({ route }) {
  if (!route?.kmMarkers || route.kmMarkers.length === 0) return null;
  return (
    <>
      <div className="section-title">구간별 페이스</div>
      <div className="card" style={{ marginBottom: 16 }}>
        {route.kmMarkers.map((k, i) => {
          const prevSec = i === 0 ? 0 : route.kmMarkers[i - 1].elapsedSec;
          return (
            <div className="cell-line" key={k.km}>
              <div>KM {k.km}</div>
              <div className="dmg" style={{ color: 'var(--text-dim)' }}>{formatPace(k.elapsedSec - prevSec)} /km</div>
            </div>
          );
        })}
      </div>
    </>
  );
}

export default function Settlement({ rideId, clientEstimatedDamage, route, onNext }) {
  const [result, setResult] = useState(null);
  const [bossInfo, setBossInfo] = useState(null);
  const [bossClaim, setBossClaim] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  // finish는 정산을 확정하는 부작용이라 ref로 한 번만 실행되게 막음
  // (개발 모드 StrictMode 이중 실행 대비 — API 자체는 재호출 안전하지만 굳이 두 번 부를 필요 없음)
  // ※ cancelled 클로저로 취소하지 않음 — StrictMode 가짜 언마운트 이후에도
  //   실제 응답을 반영해야 하는데 cancelled=true면 영영 로딩에 갇히기 때문.
  const startedRef = useRef(false);
  useEffect(() => {
    if (startedRef.current) return;
    startedRef.current = true;

    async function run() {
      setLoading(true);
      setError(null);
      try {
        const data = await finishRide(rideId, clientEstimatedDamage);
        setResult(data);

        const defeated = data.notes?.some((n) => n.includes('처치'));
        if (defeated && data.bossId) {
          // 처치 화면용 보스 상세 + 보상 실제 지급(클레임)
          // 이 두 호출이 실패해도 정산 자체는 이미 끝난 상태라 화면은 계속 보여줌
          try {
            const boss = await getBossById(data.bossId);
            setBossInfo(boss);
          } catch {
            // 보스 상세 조회 실패는 무시 — 기본 처치 배너만 보여줌
          }
          try {
            const claim = await claimAllBossRewards();
            setBossClaim(claim);
          } catch {
            // 이미 다른 화면/기기에서 클레임했을 수도 있음(409) — 무시
          }
        }
      } catch (err) {
        setError(err.message || '정산에 실패했어요.');
      } finally {
        setLoading(false);
      }
    }

    run();
  }, [rideId, clientEstimatedDamage]);

  if (loading) {
    return (
      <div className="screen">
        <div className="eyebrow">R2D · 정산 중</div>
        <h1>이번 주행을 계산하고 있어요</h1>
        <div className="spinner" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="screen">
        <div className="eyebrow">R2D · 정산 실패</div>
        <h1>정산에 문제가 생겼어요</h1>
        <div className="error-box">{error}</div>
        <button className="btn-primary" onClick={() => window.location.reload()}>다시 시도</button>
      </div>
    );
  }

  const { damage, distance, contribution, reward, cellLines = [], notes = [] } = result;
  const defeatNote = notes.find((n) => n.includes('처치'));
  const isDefeated = Boolean(defeatNote);

  return (
    <div className="screen impact-shake">
      <div className="eyebrow">R2D · 정산 결과</div>

      <div className={`boss-arena${isDefeated ? ' defeated' : ''}`}>
        <img src={riderMarkerImg} alt="" className="attacker" />
        <img src={bossDefaultImg} alt="" className="boss-img" />
        {isDefeated && (
          <div className="defeat-banner">
            <div className="defeat-title">BOSS DEFEATED</div>
            <div className="defeat-sub">
              {bossInfo?.name || defeatNote}
              {bossInfo?.myContributionRatio != null && (
                <> · 내 기여 {Math.round(bossInfo.myContributionRatio * 100)}%</>
              )}
            </div>
            {bossClaim && bossClaim.count > 0 && (
              <div className="defeat-claim">
                처치 보상 XP +{bossClaim.totalXp} · 코인 +{bossClaim.totalCoins} · 지역기여 +{bossClaim.totalRegionContributionPoints}
              </div>
            )}
            <span className="defeat-spark s1" />
            <span className="defeat-spark s2" />
            <span className="defeat-spark s3" />
            <span className="defeat-spark s4" />
            <span className="defeat-spark s5" />
            <span className="defeat-spark s6" />
          </div>
        )}
      </div>

      <div className="damage-hero">
        <div className="damage-number">-{Math.round(damage.finalDamage).toLocaleString()}</div>
        <div className="damage-combo">
          <span className="combo-tag">CONTRIBUTION ×{damage.contributionMultiplier.toFixed(2)}</span>
          <span className="combo-tag">COMBO ×{damage.deckSynergy.toFixed(2)}</span>
        </div>
        <div className="applied-badge">
          서버 확정 데미지 {Math.round(damage.appliedDamage).toLocaleString()}
          {damage.clientEstimate != null && (
            <> · 내 예상치 {Math.round(damage.clientEstimate).toLocaleString()}</>
          )}
        </div>
      </div>

      <RouteSplits route={route} />

      <div className="ride-stats">
        <div className="stat-tile">
          <div className="v">{distance.validM.toFixed(0)}m</div>
          <div className="l">유효 거리</div>
        </div>
        <div className="stat-tile">
          <div className="v">{contribution.newCells}</div>
          <div className="l">신규 구간</div>
        </div>
        <div className="stat-tile">
          <div className="v">{reward?.packGrade || '-'}</div>
          <div className="l">획득 팩</div>
        </div>
      </div>

      {distance.invalidM > 0 && (
        <div className="note-box">
          무효 처리된 거리 {distance.invalidM.toFixed(0)}m · 무효 구간 {contribution.invalidCells}개
          (GPS 신뢰도가 낮아 인정되지 않았어요)
        </div>
      )}

      {cellLines.length > 0 && (
        <>
          <div className="section-title">데미지 내역</div>
          <div className="card">
            {cellLines.map((line, i) => (
              <div className="cell-line" key={`${line.cellId}-${i}`}>
                <div>
                  <div>{line.cellId}</div>
                  <div className="note">{line.note}</div>
                </div>
                <div className="dmg">-{line.damage.toFixed(1)}</div>
              </div>
            ))}
          </div>
        </>
      )}

      {reward && (
        <>
          <div className="section-title">보상</div>
          <div className="card" style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span>XP +{reward.xp} · 코인 +{reward.coins}</span>
            <span style={{ color: 'var(--accent)' }}>지역기여 +{reward.regionContributionPoints}</span>
          </div>
        </>
      )}

      {notes.filter((n) => n !== defeatNote).length > 0 && (
        <div className="note-box">{notes.filter((n) => n !== defeatNote).join(' · ')}</div>
      )}

      <button className="btn-primary" style={{ marginTop: 24 }} onClick={onNext}>
        {reward?.packId ? '팩 열러가기' : '지도로 돌아가기'}
      </button>
    </div>
  );
}
