// 화면: 몬스터 배틀
// 지도에서 레드포인트(결함 후보) 하나를 탭하면 이 화면으로 옴.
// 코리요 캐릭터를 몬스터로 써서, 탭할 때마다 HP가 깎이고 진동(haptic)이 옴.
//
// ⚠️ 지금은 "탭해서 HP 깎기"까지만 프론트에서 구현한 상태예요. 실제 데미지량을
//    주행 데이터(거리·데크 시너지 등)로 계산하는 부분은 백엔드에 결함 후보 단위
//    정산 API가 아직 없어서 못 붙였어요 — 지금 백엔드는 /rides/{id}/finish로
//    "주행 전체" 단위로만 정산하지, "결함 후보 하나"로는 정산 안 해요.
//    이 부분은 백엔드 쪽과 상의해서 API 추가되면 여기 TODO 자리에 연결하면 됩니다.

import { useState } from 'react';
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

export default function MonsterBattle({ anomaly, onDone }) {
  const [hp, setHp] = useState(MAX_HP);
  const [hitKey, setHitKey] = useState(0);
  const defeated = hp <= 0;

  const monsterImg = MONSTER_IMAGE_BY_CLASS[anomaly?.anomalyClass] || bossDefaultImg;

  function handleAttack() {
    if (defeated) return;
    vibrate(60); // 타격마다 짧게 진동
    setHitKey((k) => k + 1);
    setHp((h) => {
      const next = Math.max(0, h - TAP_DAMAGE);
      if (next === 0) {
        vibrate([80, 60, 120]); // 처치 시 좀 더 강하게
      }
      return next;
    });
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

      {!defeated ? (
        <button className="btn-primary" style={{ marginTop: 24 }} onClick={handleAttack}>
          공격!
        </button>
      ) : (
        <button className="btn-primary" style={{ marginTop: 24 }} onClick={onDone}>
          지도로 돌아가기
        </button>
      )}
    </div>
  );
}
