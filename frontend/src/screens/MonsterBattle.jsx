// 화면: 몬스터 배틀
// 지도에서 레드포인트(결함 후보) 하나를 탭하면 이 화면으로 옴.
// 헬멧 쓴 코리요들 = 몬스터(나쁜 애들), 헬멧 안 쓴 기본 코리요 = 착한 애(공격하는 쪽 캐릭터).
// 탭할 때마다 HP가 깎이고 진동(haptic)이 옴.
// HP가 0이 되면 실제 백엔드 배틀 API를 호출해서 보상을 받아옴 — 덱 편성(3장) 시너지가
// 반영된 진짜 보상이 나옴 (POST /api/v1/anomalies/{cellId}/battle).
//
// ⚠️ 지금 반영된 건 "덱 시너지"까지입니다. 주행거리 기반 데미지는 아직 이 API로 못 가져와요 —
//    그건 네비게이터가 들고 있는 실주행 데이터라 네비게이터↔게임 연동이 되어야 붙습니다.

import { useRef, useState } from 'react';
import { battleAnomaly } from '../api';
import koriyoGoodImg from '../assets/monsters/koriyo_good.png';
import dalkongiPink from '../assets/monsters/dalkongi_pink.png';
import dalkongiPurple from '../assets/monsters/dalkongi_purple.png';
import dalkongiMint from '../assets/monsters/dalkongi_mint.png';
import dalkongiYellow from '../assets/monsters/dalkongi_yellow.png';
import dalkongiBlue from '../assets/monsters/dalkongi_blue.png';
import dalkongiGreen from '../assets/monsters/dalkongi_green.png';
import dalkongiOrange from '../assets/monsters/dalkongi_orange.png';
import dalkongiSky from '../assets/monsters/dalkongi_sky.png';
import dalkongiLime from '../assets/monsters/dalkongi_lime.png';
import dalkongiWhite from '../assets/monsters/dalkongi_white.png';
import tyrexGreen from '../assets/monsters/tyrex_green.png';
import tyrexBlack from '../assets/monsters/tyrex_black.png';
import tyrexStorm from '../assets/monsters/tyrex_storm.png';

const MAX_HP = 100;
const TAP_DAMAGE = 18;

// 헬멧 쓴 애들 = "나쁜 애들"(몬스터). 헬멧 안 쓴 코리요(koriyoGoodImg)는
// "착한 애"라서 몬스터로 안 쓰고, 라이더(공격하는 쪽) 이미지로 씀.
const MONSTER_ROSTER = [
  dalkongiPink, dalkongiPurple, dalkongiMint, dalkongiYellow, dalkongiBlue,
  dalkongiGreen, dalkongiOrange, dalkongiSky, dalkongiLime, dalkongiWhite,
  tyrexGreen, tyrexBlack, tyrexStorm,
];

// 결함 후보(cellId)마다 항상 같은 몬스터가 나오도록 간단한 해시로 로스터에서 하나 고름.
// 결함 유형(anomalyClass)은 지금 2종류뿐이라 그걸로 나누면 몬스터가 2종류밖에 안 나와서,
// 대신 지점마다 다르게 보이도록 cellId 기준으로 골랐음.
function pickMonster(cellId) {
  if (!cellId) return MONSTER_ROSTER[0];
  let hash = 0;
  for (let i = 0; i < cellId.length; i++) {
    hash = (hash * 31 + cellId.charCodeAt(i)) >>> 0;
  }
  return MONSTER_ROSTER[hash % MONSTER_ROSTER.length];
}

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

  const monsterImg = pickMonster(anomaly?.cellId);

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
        <img src={koriyoGoodImg} alt="" className="attacker" />
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
