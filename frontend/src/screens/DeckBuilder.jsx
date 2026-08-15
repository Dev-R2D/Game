// 화면 ③: 덱 편성
// 카드 도감에서 3장을 고르면 /decks/preview로 시너지를 미리 보여줌.
// 3장이 다 채워지기 전까지는 주행 시작 버튼이 비활성화됨.

import { useEffect, useState } from 'react';
import { getCardCatalog, previewDeck } from '../api';
import BottomNav from '../components/BottomNav';
import { IconChevronLeft } from '../components/Icons';

export default function DeckBuilder({ regionCode, onBack, onNext, onNavigate }) {
  const [cards, setCards] = useState(null);
  const [error, setError] = useState(null);
  const [selected, setSelected] = useState([]); // cardCode 배열, 최대 3
  const [preview, setPreview] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [starting, setStarting] = useState(false);

  useEffect(() => {
    getCardCatalog()
      .then(setCards)
      .catch((err) => setError(err.message || '카드 도감을 불러오지 못했어요.'));
  }, []);

  useEffect(() => {
    if (selected.length !== 3) {
      setPreview(null);
      return;
    }
    let cancelled = false;
    setPreviewLoading(true);
    previewDeck(selected)
      .then((data) => {
        if (!cancelled) setPreview(data);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || '시너지 계산에 실패했어요.');
      })
      .finally(() => {
        if (!cancelled) setPreviewLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [selected]);

  function toggleCard(code) {
    setSelected((prev) => {
      if (prev.includes(code)) return prev.filter((c) => c !== code);
      if (prev.length >= 3) return prev;
      return [...prev, code];
    });
  }

  const cardByCode = Object.fromEntries((cards || []).map((c) => [c.code, c]));

  return (
    <div className="app-shell">
      <div className="top-bar">
        <button className="icon-btn" onClick={onBack}><IconChevronLeft /></button>
        <div style={{ fontSize: 13, fontWeight: 700 }}>덱 편성</div>
        <div style={{ width: 40 }} />
      </div>

      <div className="app-content">
        <div className="eyebrow">R2D · 3장 선택</div>
        <h1 style={{ fontSize: 22 }}>오늘의 라이딩 덱</h1>
        <p className="subtitle" style={{ marginBottom: 16 }}>
          같은 계열로 모으면 시너지가 올라가요. 계열을 섞으면 손해는 적지만 최대 배율이 낮아요.
        </p>

        <div className="deck-slots">
          {[0, 1, 2].map((i) => {
            const code = selected[i];
            const card = code ? cardByCode[code] : null;
            return (
              <div key={i} className={`deck-slot${card ? ' filled' : ''}`}>
                {card ? (
                  <>
                    <div>{card.name}</div>
                    <div style={{ color: 'var(--accent)', fontSize: 10 }}>{card.line} · T{card.tier}</div>
                  </>
                ) : (
                  `슬롯 ${i + 1}`
                )}
              </div>
            );
          })}
        </div>

        {selected.length === 3 && (
          <div className="synergy-box">
            {previewLoading && <div className="spinner" style={{ margin: '8px auto' }} />}
            {preview && !previewLoading && (
              <>
                <div className="synergy-value">×{preview.synergy.toFixed(2)}</div>
                <div className="synergy-label">{preview.synergyLabel}</div>
                <div className="synergy-note">{preview.note}</div>
                {preview.contributionBonus && Object.keys(preview.contributionBonus).length > 0 && (
                  <div style={{ display: 'flex', gap: 8, marginTop: 10, flexWrap: 'wrap' }}>
                    {Object.entries(preview.contributionBonus).map(([k, v]) => (
                      <span className="combo-tag" key={k}>{k} +{Math.round(v * 100)}%</span>
                    ))}
                  </div>
                )}
              </>
            )}
          </div>
        )}

        {error && <div className="error-box">{error}</div>}

        <div className="section-title">카드 도감</div>
        {!cards && !error && <div className="spinner" />}
        <div className="card-grid">
          {(cards || []).map((c) => (
            <button
              type="button"
              key={c.code}
              className={`tap-card card-tile${selected.includes(c.code) ? ' selected' : ''}${c.locked ? ' locked' : ''}`}
              disabled={c.locked}
              aria-pressed={selected.includes(c.code)}
              onClick={() => toggleCard(c.code)}
            >
              <div className="tier">T{c.tier}</div>
              <div className="line-tag">{c.line}</div>
              <div className="name">{c.name}</div>
              {c.description && <div className="desc">{c.description}</div>}
              {c.locked && <div className="desc" style={{ color: 'var(--gold)', marginTop: 4 }}>🔒 검증 대기 중</div>}
            </button>
          ))}
        </div>
      </div>

      <div style={{ padding: '0 20px 16px' }}>
        <button
          className="btn-primary"
          disabled={selected.length !== 3 || starting}
          onClick={() => {
            setStarting(true);
            onNext(regionCode, selected);
          }}
        >
          {selected.length}/3장 선택됨 · 주행 시작
        </button>
      </div>
      <BottomNav screen="deck" onNavigate={onNavigate} />
    </div>
  );
}
