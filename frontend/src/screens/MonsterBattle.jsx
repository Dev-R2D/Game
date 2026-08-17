// 화면: 몬스터 배틀
// 지도에서 레드포인트(결함 후보) 하나를 탭하면 이 화면으로 옴.
// 코리요 캐릭터를 몬스터로 써서, 탭할 때마다 HP가 깎이고 진동(haptic)이 옴.
// HP가 0이 되면 실제 백엔드 배틀 API를 호출해서 보상을 받아옴 — 덱 편성(3장) 시너지가
// 반영된 진짜 보상이 나옴 (POST /api/v1/anomalies/{cellId}/battle).
//
// ⚠️ 지금 반영된 건 "덱 시너지"까지입니다. 주행거리 기반 데미지는 아직 이 API로 못 가져와요 —
//    그건 네비게이터가 들고 있는 실주행 데이터라 네비게이터↔게임 연동이 되어야 붙습니다.

import { useRef, useState } from 'react';
import { battleAnomaly } from '../api';
import bossDefaultImg from '../assets/boss-default.png';
import riderMarkerImg from '../assets/rider-marker.png';

const MAX_HP = 100;
const TAP_DAMAGE = 18;

// 결함 유형별로 다른 코리요 몬스터를 쓰고 싶으면 여기에 이미지를 매핑하면 됨.
// 지금은 코리요 이미지 에셋이 아직 없어서 기본 보스 이미지로 대체함.
const MONSTER_IMAGE_BY_CLASS = {
  // '충격성 이상 후보': koriyoBumpImg,
  // '반복 진동성 이상 후보': koriyoShakeImg,
};

function vibrate(pattern) {
  if (navigator.vibrate) navigator.vibrate(pattern);
}

export default function MonsterBattle({ anomaly, deckCardCodes, onDone }) {
  const [hp, setHp] = useState(MAX_HP);
  const [hitKey, setHitKey] = useState(0);
  const [reward, setReward] = useState(null); // 서버가 돌려준 실제 보상
  const [rewardError, setRewardError] = useState(null);
  const [claiming, setClaiming] = useState(false);
  const defeated = hp <= 0;
  // hp state는 연타 시 리렌더 사이에 갱신이 안 끝나서 defeated 체크만으로는 중복 호출을 못 막음
  // (연속 클릭하면 claimReward가 여러 번 불려서 서버에 중복 배틀 요청이 감). ref로 한 번만 막음.
  const claimedRef = useRef(false);

  const monsterImg = MONSTER_IMAGE_BY_CLASS[anomaly?.anomalyClass] || bossDefaultImg;

  function handleAttack() {
    if (defeated) return;
    vibrate(60); // 타격마다 짧게 진동
    setHitKey((k) => k + 1);
    setHp((h) => {
      const next = Math.max(0, h - TAP_DAMAGE);
      if (next === 0 && !claimedRef.current) {
        claimedRef.current = true;
        vibrate([80, 60, 120]); // 처치 시 좀 더 강하게
        claimReward();
      }
      return next;
    });
  }

  function claimReward() {
    setClaiming(true);
    setRewardError(null);
    battleAnomaly(anomaly?.cellId, deckCardCodes)
      .then(setReward)
      .catch((err) => setRewardError(err.message || '보상 지급에 실패했어요.'))
      .finally(() => setClaiming(false));
  }

  return (
    <div className="screen impact-shake">
      <div className="eyebrow">R2D · 몬스터 배틀</div>
      <h1>{anomaly?.anomalyClass || '결함 후보'}</h1>
      {anomaly?.note && <p className="hint">{anomaly.note}</p>}

      <div className={`boss-arena${defeated ? ' defeated' : ''}`}>
        <img src={riderMarkerImg} alt="" className="attacker" />
        <img key={hitKey} src={monsterImg} alt="몬스터" className="boss-img" />
        {defeated && (
          <div className="defeat-banner">
            <div className="defeat-title">DEFEATED</div>
            <div className="defeat-sub">결함 후보 확인 완료</div>
            <span className="defeat-spark s1" />
            <span className="defeat-spark s2" />
            <span className="defeat-spark s3" />
          </div>
        )}
      </div>

      <div className="section-title">몬스터 HP</div>
      <div className="hp-track">
        <div className="hp-segment">
          <div className="fill" style={{ width: `${hp}%` }} />
        </div>
      </div>
      <p className="hint" style={{ marginTop: 6 }}>{hp} / {MAX_HP}</p>

      {defeated && (
        <>
          {claiming && <div className="spinner" style={{ marginTop: 16 }} />}

          {rewardError && (
            <div className="error-box" style={{ marginTop: 16 }}>
              {rewardError}
              <button className="btn-secondary" style={{ marginTop: 8 }} onClick={claimReward}>다시 시도</button>
            </div>
          )}

          {reward && (
            <div className="card" style={{ marginTop: 16 }}>
              <div className="section-title" style={{ marginTop: 0 }}>
                {reward.alreadyBattled ? '이미 처치한 몬스터예요' : '보상'}
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>XP +{reward.xp} · 코인 +{reward.coins}</span>
                <span style={{ color: 'var(--accent)' }}>지역기여 +{reward.regionContributionPoints}</span>
              </div>
              {reward.deckSynergy !== 1 && (
                <div className="note" style={{ marginTop: 6 }}>
                  덱 시너지 ×{reward.deckSynergy.toFixed(2)} ({reward.deckSynergyLabel}) 적용됨
                </div>
              )}
            </div>
          )}
        </>
      )}

      {!defeated ? (
        <button className="btn-primary" style={{ marginTop: 24 }} onClick={handleAttack}>
          공격!
        </button>
      ) : (
        <button className="btn-primary" style={{ marginTop: 24 }} onClick={onDone} disabled={claiming}>
          지도로 돌아가기
        </button>
      )}
    </div>
  );
}
