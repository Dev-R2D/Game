// 화면 ⑥ / ⑧: 탐사 팩 개봉
// GET /packs/pending 으로 개봉 대기 팩 목록을 받고, 클릭하면 POST /packs/{packId}/open으로 개봉.
// 개봉 전(내용물 비공개)과 개봉 후(카드·코인 공개) 화면을 등급별 색상으로 구분함.

import { useEffect, useState } from 'react';
import { getPendingPacks, openPack, getCardCatalog } from '../api';
import BottomNav from '../components/BottomNav';
import { IconGift, IconCards, IconCoin } from '../components/Icons';

export default function PackOpen({ onNext, onNavigate }) {
  const [packs, setPacks] = useState(null);
  const [error, setError] = useState(null);
  const [opening, setOpening] = useState(null);
  const [opened, setOpened] = useState({});
  const [cardNames, setCardNames] = useState({});

  useEffect(() => {
    getPendingPacks()
      .then(setPacks)
      .catch((err) => setError(err.message || '팩 정보를 불러오지 못했어요.'));
    getCardCatalog()
      .then((cards) => {
        setCardNames(Object.fromEntries(cards.map((c) => [c.code, c])));
      })
      .catch(() => {});
  }, []);

  async function handleOpen(packId) {
    setOpening(packId);
    setError(null);
    try {
      const result = await openPack(packId);
      setOpened((prev) => ({ ...prev, [packId]: result }));
    } catch (err) {
      setError(err.message || '팩 개봉에 실패했어요.');
    } finally {
      setOpening(null);
    }
  }

  return (
    <div className="app-shell">
      <div className="app-content">
        <div className="eyebrow">R2D · 탐사 팩</div>
        <h1 style={{ fontSize: 22 }}>획득한 팩을 열어보세요</h1>
        <p className="subtitle">등급이 높을수록 더 좋은 카드와 코인이 들어있어요.</p>

        {error && <div className="error-box">{error}</div>}
        {!packs && !error && <div className="spinner" />}

        {packs && packs.length === 0 && (
          <div className="empty-state">개봉 대기 중인 팩이 없어요. 주행을 마치면 팩이 쌓여요.</div>
        )}

        {(packs || []).map((pack) => {
          const result = opened[pack.id];
          const isOpen = Boolean(result?.opened);

          return (
            <div key={pack.id} style={{ marginBottom: 16 }}>
              <button
                type="button"
                className={`tap-card pack-card grade-${pack.grade}`}
                disabled={isOpen || Boolean(opening)}
                onClick={() => handleOpen(pack.id)}
              >
                {!isOpen ? (
                  <>
                    <div className="pack-icon"><IconGift /></div>
                    <span className={`badge grade-${pack.grade}`}>{pack.grade} 등급</span>
                    <div style={{ marginTop: 10, fontWeight: 800, fontSize: 16 }}>
                      {opening === pack.id ? '개봉 중...' : '탭해서 개봉하기'}
                    </div>
                    <div style={{ marginTop: 6, fontSize: 12, color: 'var(--text-dim)' }}>
                      {pack.gradeReason}
                    </div>
                  </>
                ) : (
                  <>
                    <span className={`badge grade-${result.grade}`}>{result.grade} 등급 개봉 완료</span>
                    <div style={{ margin: '14px 0' }} className="reveal-cards">
                      {result.cardCodes.map((code, i) => {
                        const card = cardNames[code];
                        return (
                          <div className="flip-card" key={`${code}-${i}`}>
                            <div className="flip-card-inner" style={{ animationDelay: `${i * 0.15}s` }}>
                              <div className="flip-face front">
                                <IconCards />
                              </div>
                              <div className="flip-face back">
                                <IconCards style={{ fontSize: 20 }} />
                                <div className="name">{card?.name || code}</div>
                                {card && <div className="tier">{card.line} · T{card.tier}</div>}
                              </div>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                    <div className="coin-pill"><IconCoin /> 코인 +{result.coins}</div>
                    <div style={{ marginTop: 10, fontSize: 11, color: 'var(--text-dim)' }}>
                      {result.gradeReason}
                    </div>
                  </>
                )}
              </button>
            </div>
          );
        })}

        <button className="btn-primary" style={{ marginTop: 12 }} onClick={onNext}>
          지도로 이동
        </button>
      </div>
      <BottomNav screen="pack" onNavigate={onNavigate} />
    </div>
  );
}
