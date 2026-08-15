// 화면 ②: 미션 / 보스 선택
// 지역을 고르면 그 지역의 오늘의 미션과 진행 중인 보스를 보여줌.
// 미션은 정보 제공용이고(실제로 서버에 넘기지는 않음), 다음 화면(③ 덱 편성)으로 넘어갈 때
// 여기서 고른 regionCode를 그대로 들고 감.

import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { getMissions, getBoss, getBossById, getPendingBossRewards, claimAllBossRewards } from '../api';
import { distanceMeters } from '../geolocation';
import BottomNav from '../components/BottomNav';
import { IconAlert } from '../components/Icons';
import bossDefaultImg from '../assets/boss-default.png';

// 행정동 대략적인 중심 좌표. 서버는 지역별 좌표를 안 갖고 있어서(셀 단위로만 관리)
// "내 위치에서 가장 가까운 지역"을 고르는 용도로 프론트에서만 근사치로 씀.
const REGIONS = [
  { code: 'DONGTAN2', label: '동탄2동', lat: 37.2000, lon: 127.1000 },
  { code: 'YEONGTONG', label: '영통동', lat: 37.2481, lon: 127.0468 },
  { code: 'GWANGGYO', label: '광교동', lat: 37.2904, lon: 127.0479 },
];

// 사용자 위치에서 가장 가까운 지역 코드. 위치를 못 가져오면 null(호출부가 기본값 유지).
function nearestRegionCode(lat, lon) {
  let nearest = null;
  let minDist = Infinity;
  for (const r of REGIONS) {
    const d = distanceMeters(lat, lon, r.lat, r.lon);
    if (d < minDist) {
      minDist = d;
      nearest = r.code;
    }
  }
  return nearest;
}

const TYPE_LABEL = { EXPLORE: '개척', UPDATE: '갱신', VERIFY: '검증', BOSS: '보스전' };

export default function MissionSelect({ onNext, onNavigate }) {
  const [regionCode, setRegionCode] = useState('DONGTAN2');
  const [missions, setMissions] = useState(null);
  const [boss, setBoss] = useState(null);
  const [selectedMission, setSelectedMission] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  // 보스 체력바를 0에서 실제 값까지 차오르게 해서 "지금까지 깎인 데미지"가
  // 눈앞에서 채워지는 느낌을 줌 (그냥 정적으로 뜨는 것보다 임팩트가 있음)
  const [hpRevealed, setHpRevealed] = useState(false);
  // 다른 라이더가 마무리한 보스의 처치·단계 보상이 쌓여있을 수 있어서 앱 진입 시 한 번 확인
  const [pendingClaim, setPendingClaim] = useState(null);
  // 그 보상 중에 "보스 처치"가 섞여있으면(내가 막타를 안 쳤어도 다같이 잡은 거라서) 배너 대신
  // 처치 연출을 보여줌 — 정산 화면은 막타를 친 사람한테만 뜨니까, 나머지 참여자는 여기서만 봄
  const [defeatedBoss, setDefeatedBoss] = useState(null);

  // 앱 진입 시 한 번 실제 위치를 물어서, 그 근처 지역으로 자동 전환.
  // 권한을 거부하거나 위치를 못 가져오면 조용히 기본 지역(동탄2동)에 머무름 —
  // 어차피 REGIONS 탭에서 직접 고를 수 있으니 막을 이유는 없음.
  useEffect(() => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const nearest = nearestRegionCode(pos.coords.latitude, pos.coords.longitude);
        if (nearest) setRegionCode(nearest);
      },
      () => {
        // 권한 거부·타임아웃 등 — 기본 지역 유지
      },
      { enableHighAccuracy: false, timeout: 8000, maximumAge: 300_000 },
    );
  }, []);

  useEffect(() => {
    let cancelled = false;
    getPendingBossRewards()
      .then((pending) => {
        if (cancelled || !pending || pending.length === 0) return;
        return claimAllBossRewards().then((claim) => {
          if (cancelled) return;
          const defeatReward = claim.rewards?.find((r) => r.kind === '보스 처치');
          if (!defeatReward) {
            setPendingClaim(claim);
            return;
          }
          const defeatRewards = claim.rewards.filter((r) => r.kind === '보스 처치');
          const summary = {
            xp: defeatRewards.reduce((s, r) => s + r.xp, 0),
            coins: defeatRewards.reduce((s, r) => s + r.coins, 0),
            points: defeatRewards.reduce((s, r) => s + r.regionContributionPoints, 0),
          };
          getBossById(defeatReward.bossId)
            .then((boss) => {
              if (!cancelled) setDefeatedBoss({ name: boss.name, tier: boss.tier, ...summary });
            })
            .catch(() => {
              if (!cancelled) setDefeatedBoss({ name: '지역 보스', tier: '', ...summary });
            });
          // 처치 보상 말고 다른 보상(단계 돌파 등)도 섞여있었다면 그건 그대로 배너로 알림
          const hasOtherRewards = claim.rewards.some((r) => r.kind !== '보스 처치');
          if (hasOtherRewards) setPendingClaim(claim);
        });
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setSelectedMission(null);

    setHpRevealed(false);

    Promise.all([getMissions(regionCode), getBoss(regionCode)])
      .then(([missionData, bossData]) => {
        if (cancelled) return;
        setMissions(missionData);
        setBoss(bossData);
        // 다음 프레임에 켜야 0% → 목표치로 CSS transition이 실제로 재생됨
        requestAnimationFrame(() => {
          requestAnimationFrame(() => {
            if (!cancelled) setHpRevealed(true);
          });
        });
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err.message || '정보를 불러오지 못했어요.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [regionCode]);

  return (
    <div className="app-shell">
      <div className="top-bar">
        <div className="rider-chip">
          <div className="avatar">R</div>
          <div>
            <div className="name">RIDER</div>
            <div className="sub">{REGIONS.find((r) => r.code === regionCode)?.label}</div>
          </div>
        </div>
      </div>

      {defeatedBoss && createPortal(
        // app-shell에 걸린 진입 애니메이션(transform)이 fixed 포지셔닝의 containing block을
        // 가둬버려서(스크롤에 끌려다님) body에 직접 붙여야 진짜 전체화면으로 뜸
        <div className="boss-defeat-overlay" onClick={() => setDefeatedBoss(null)}>
          <div className="boss-arena defeated" style={{ height: 220, width: '86vw', maxWidth: 360 }}>
            <img src={bossDefaultImg} alt="" className="boss-img" />
            <div className="defeat-banner">
              <div className="defeat-title">BOSS DEFEATED</div>
              <div className="defeat-sub">
                {defeatedBoss.name}{defeatedBoss.tier ? ` · ${defeatedBoss.tier}` : ''}<br />
                우리 지역 라이더들이 함께 처치했어요
              </div>
              <div className="defeat-claim">
                XP +{defeatedBoss.xp} · 코인 +{defeatedBoss.coins} · 지역기여 +{defeatedBoss.points}
              </div>
              <span className="defeat-spark s1" /><span className="defeat-spark s2" />
              <span className="defeat-spark s3" /><span className="defeat-spark s4" />
              <span className="defeat-spark s5" /><span className="defeat-spark s6" />
            </div>
          </div>
          <button className="btn-primary boss-defeat-dismiss" onClick={() => setDefeatedBoss(null)}>확인</button>
        </div>,
        document.body,
      )}

      <div className="app-content">
        {pendingClaim && pendingClaim.count > 0 && (
          <div className="pending-claim-banner" onClick={() => setPendingClaim(null)}>
            <strong>보스 보상 도착!</strong> 다른 라이더들과 함께 처치한 보스에서
            XP +{pendingClaim.totalXp} · 코인 +{pendingClaim.totalCoins} · 지역기여 +{pendingClaim.totalRegionContributionPoints}
            받았어요.
          </div>
        )}

        <div className="eyebrow">R2D · 미션 선택</div>
        <h1 style={{ fontSize: 22 }}>오늘의 도로를 골라주세요</h1>
        <p className="subtitle" style={{ marginBottom: 16 }}>
          지역마다 남은 미탐사 구간과 보스 체력이 달라요.
        </p>

        <div className="btn-row" style={{ marginBottom: 18, flexWrap: 'wrap', gap: 8 }}>
          {REGIONS.map((r) => (
            <button
              key={r.code}
              className="btn-secondary"
              style={{
                flex: '1 1 30%',
                borderColor: regionCode === r.code ? 'var(--accent)' : 'var(--line)',
                color: regionCode === r.code ? 'var(--accent)' : 'var(--text)',
              }}
              onClick={() => setRegionCode(r.code)}
            >
              {r.label}
            </button>
          ))}
        </div>

        {loading && <div className="spinner" />}
        {error && <div className="error-box">{error}</div>}

        {!loading && !error && boss && (
          <>
            <div className="section-title">지역 보스</div>
            <div className="boss-card">
              <div className="boss-portrait">
                <img src={bossDefaultImg} alt={boss.name} />
              </div>
              <div className="boss-label">REGION BOSS · {boss.tier}</div>
              <div className="boss-name-row">
                <div className="boss-name">{boss.name}</div>
                <div className="boss-pct">{Math.round((boss.progressRatio || 0) * 100)}%</div>
              </div>
              <div className="hp-track">
                {Array.from({ length: boss.phaseCount || 1 }).map((_, i) => {
                  const phaseIdx = i + 1;
                  const isPast = phaseIdx < boss.phase;
                  const isCurrent = phaseIdx === boss.phase;
                  const fillPct = isPast ? 100 : isCurrent ? (boss.progressRatio || 0) * boss.phaseCount * 100 - i * 100 : 0;
                  const targetPct = Math.max(0, Math.min(100, fillPct));
                  return (
                    <div className="hp-segment" key={i}>
                      <div className="fill" style={{ width: `${hpRevealed ? targetPct : 0}%` }} />
                    </div>
                  );
                })}
              </div>
              <div className="boss-meta">
                <span>단계 {boss.phase} / {boss.phaseCount}</span>
                <span>{boss.currentHp?.toLocaleString()} / {boss.maxHp?.toLocaleString()} HP</span>
              </div>
              <div className="boss-meta">
                <span>참여 라이더 {boss.participants}명</span>
                <span>{boss.status}</span>
              </div>
            </div>
          </>
        )}

        {!loading && !error && missions && (
          <>
            <div className="section-title">오늘의 미션</div>
            {missions.map((m) => (
              <button
                type="button"
                key={m.code}
                className={`tap-card mission-card${selectedMission?.code === m.code ? ' selected' : ''}`}
                aria-pressed={selectedMission?.code === m.code}
                onClick={() => setSelectedMission(m)}
              >
                <div className="mission-top">
                  <div className="mission-title">{m.title}</div>
                  <span className={`badge type-${m.type}`}>{TYPE_LABEL[m.type] || m.type}</span>
                </div>
                <div className="mission-summary">{m.summary}</div>
                <div className="mission-meta-row">
                  <span>구간 {m.targetCellCount}개</span>
                  <span>{m.estimatedMinutesMin}~{m.estimatedMinutesMax}분</span>
                  <span>추천 라인 {m.recommendedLine}</span>
                </div>
                <div className="mission-meta-row" style={{ marginTop: 6, color: 'var(--accent)' }}>
                  {m.rewardSummary}
                </div>
                {selectedMission?.code === m.code && m.safetyNote && (
                  <div className="mission-safety">
                    <IconAlert style={{ verticalAlign: '-3px', marginRight: 4 }} />
                    {m.safetyNote}
                  </div>
                )}
              </button>
            ))}
            {missions.length === 0 && (
              <div className="empty-state">이 지역은 오늘 진행할 미션이 없어요. 이미 잘 정화된 도로예요!</div>
            )}
          </>
        )}
      </div>

      <div style={{ padding: '0 20px 16px' }}>
        <button className="btn-primary" onClick={() => onNext(regionCode)} disabled={loading}>
          {selectedMission ? `'${selectedMission.title}' 으로 덱 편성하기` : '덱 편성하러 가기'}
        </button>
      </div>
      <BottomNav screen="missions" onNavigate={onNavigate} />
    </div>
  );
}
