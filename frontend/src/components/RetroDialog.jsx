// 포켓몬류 게임 감성의 도트 대화창.
// lines를 한 줄씩 보여주고, 탭하면 다음 줄로 — 마지막 줄에서 한 번 더 탭하면 onFinish 호출.
// 화면 진행(버튼 등) 자체는 막지 않고 위에 얹히는 연출용 오버레이라, 무시하고 넘어가도
// 기존 온보딩 흐름은 그대로 동작함.

import { useState } from 'react';

export default function RetroDialog({ lines, speaker = 'R2D 시스템', onFinish }) {
  const [index, setIndex] = useState(0);
  if (!lines || lines.length === 0) return null;

  const isLast = index === lines.length - 1;

  function advance() {
    if (isLast) {
      onFinish?.();
    } else {
      setIndex((i) => i + 1);
    }
  }

  return (
    <button type="button" className="retro-dialog" onClick={advance}>
      <span className="retro-dialog-speaker">{speaker}</span>
      <span className="retro-dialog-text">{lines[index]}</span>
      <span className="retro-dialog-cursor">{isLast ? '●' : '▼'}</span>
    </button>
  );
}
