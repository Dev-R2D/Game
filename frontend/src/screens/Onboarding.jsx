// 온보딩: 스플래시 → SCAN 01~03 슬라이드 → 위치 권한 안내
// 실제 인증(Google 로그인 등)은 없음 — 여기서는 브랜드 소개와 위치 권한 설명만 하고
// 끝나면 onDone()을 호출해서 App.jsx가 닉네임 등록 화면으로 넘겨줌.

import { useState } from 'react';
import { IconArrowRight, IconPin } from '../components/Icons';
import RetroDialog from '../components/RetroDialog';
import riderHeroImg from '../assets/rider-hero.png';

const SLIDES = [
  {
    tag: '01 · THE UNSEEN CITY',
    title: ['당신의 도시는', '아직 스캔되지 않았습니다'],
    body: '익숙한 도로 위에도 아직 기록되지 않은 위험이 남아 있습니다.',
  },
  {
    tag: '02 · RIDE TO REVEAL',
    title: ['달린 만큼', '지도가 열립니다'],
    body: '자전거가 지나간 궤적은 도시를 밝히고 오래된 데이터를 다시 깨웁니다.',
  },
  {
    tag: '03 · FIND WHAT HIDES',
    title: ['그리고 도로에 숨은', '것들이 보입니다'],
    body: '라이더의 기록이 쌓이면 의심 지점은 검증되고 도시의 몸으로 드러납니다.',
  },
];

// 각 단계에서 뜨는 포켓몬류 감성 대화창 대사
const DIALOG_LINES = {
  splash: ['어서 오세요, 라이더!', 'R2D 시스템이 당신의 자전거를 기다리고 있었습니다.'],
  slide: [
    ['이 도시는... 안개에 가려져 있군요.', '아직 아무도 달리지 않은 길이 너무 많아요.'],
    ['당신이 페달을 밟는 만큼', '안개가 걷히고 지도가 또렷해질 거예요!'],
    ['가끔은 길 위에 이상한 흔적도 보일 거예요.', '여러 라이더가 함께 확인하면 진짜인지 알 수 있어요.'],
  ],
  location: ['마지막으로 하나만요!', '위치 권한을 켜야 화면을 꺼도 기록이 안 끊겨요.'],
};

const STEP = { SPLASH: 0, SLIDE1: 1, SLIDE2: 2, SLIDE3: 3, LOCATION: 4 };

export default function Onboarding({ onDone }) {
  const [step, setStep] = useState(STEP.SPLASH);
  const [dialogDismissed, setDialogDismissed] = useState({});

  function dismissDialog(key) {
    setDialogDismissed((prev) => ({ ...prev, [key]: true }));
  }

  if (step === STEP.SPLASH) {
    return (
      <div className="onb-shell">
        <div className="splash-visual">
          <img src={riderHeroImg} alt="" className="splash-rider" />
          <div className="splash-logo">
            R2<span className="d">D</span>
          </div>
          <div className="splash-sub">ROAD TO DATA</div>
          <svg className="splash-line" viewBox="0 0 400 80" preserveAspectRatio="none">
            <path d="M10 65 C 120 60, 260 20, 390 15" />
            <circle cx="390" cy="15" r="5" />
          </svg>
        </div>
        <div className="splash-panel">
          <div className="splash-tagline">도시를 달리고</div>
          <div className="splash-headline">보이지 않던 길을 깨우세요</div>
          <button className="btn-primary" onClick={() => setStep(STEP.SLIDE1)}>
            시작하기
          </button>
        </div>
        {!dialogDismissed.splash && (
          <RetroDialog lines={DIALOG_LINES.splash} onFinish={() => dismissDialog('splash')} />
        )}
      </div>
    );
  }

  if (step >= STEP.SLIDE1 && step <= STEP.SLIDE3) {
    const idx = step - 1;
    const slide = SLIDES[idx];
    const isLast = step === STEP.SLIDE3;

    return (
      <div className="onb-shell">
        <button className="onb-skip" onClick={() => setStep(STEP.LOCATION)}>
          건너뛰기
        </button>
        <div className="onb-visual">
          <div className="onb-blob" style={{ width: 220, height: 220, top: '10%', left: '15%' }} />
          <div className="onb-blob" style={{ width: 160, height: 160, top: '40%', right: '10%', animationDelay: '2s' }} />
          <div className="onb-scan-label">SCAN 0{step}</div>
        </div>
        <div className="onb-panel">
          <div className="onb-eyebrow">{slide.tag}</div>
          <h1 className="onb-title">
            {slide.title.map((line, i) => (
              <span key={i}>
                {line}
                {i < slide.title.length - 1 && <br />}
              </span>
            ))}
          </h1>
          <p className="onb-subtitle">{slide.body}</p>
          <div className="onb-footer">
            <div className="onb-dots">
              {SLIDES.map((_, i) => (
                <span key={i} className={i === idx ? 'active' : ''} />
              ))}
            </div>
            <button
              className="onb-next-btn"
              onClick={() => setStep(isLast ? STEP.LOCATION : step + 1)}
            >
              {isLast ? '시작' : <IconArrowRight />}
            </button>
          </div>
        </div>
        <div className="onb-foot-hint">주행으로 알려 도시의 변화를 확인하세요</div>
        {!dialogDismissed[`slide${idx}`] && (
          <RetroDialog lines={DIALOG_LINES.slide[idx]} onFinish={() => dismissDialog(`slide${idx}`)} />
        )}
      </div>
    );
  }

  // STEP.LOCATION
  return (
    <div className="onb-shell">
      <div className="loc-topbar">
        <span className="step-label">권한 설정</span>
        <div className="loc-progress">
          <span className="done" />
          <span className="done" />
          <span />
        </div>
        <span className="step-label">1/3</span>
      </div>

      <div className="loc-radar-wrap">
        <div className="loc-radar-ring r1" />
        <div className="loc-radar-ring r2" />
        <div className="loc-radar-ring r3" />
        <div className="loc-radar-core">
          <IconPin />
        </div>
      </div>

      <div className="loc-panel">
        <div className="onb-eyebrow">BACKGROUND LOCATION</div>
        <h1 className="onb-title">위치 접근을<br />허용해 주세요</h1>
        <p className="onb-subtitle" style={{ marginBottom: 20 }}>
          화면을 꺼도 주행 궤적과 미탐사 구간을 정확히 기록하는 데 필요합니다.
        </p>

        <div className="loc-card">
          <span className="always-badge">항상 허용</span>
          <span className="desc">백그라운드에서도 경로 기록</span>
        </div>

        <button className="btn-primary" onClick={onDone}>
          위치 항상 허용
        </button>
        <button className="btn-ghost" style={{ width: '100%', textAlign: 'center' }} onClick={onDone}>
          거부 상황 시연
        </button>
        <p className="onb-foot-hint">R2D는 주행 분석에 필요한 최소 정보만 사용합니다.</p>
      </div>
      {!dialogDismissed.location && (
        <RetroDialog lines={DIALOG_LINES.location} onFinish={() => dismissDialog('location')} />
      )}
    </div>
  );
}
