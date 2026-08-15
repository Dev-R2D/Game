// 이모지 대신 쓰는 커스텀 라인 아이콘 세트. 전부 currentColor를 써서 부모의 색을 그대로 물려받음.
// 24x24 기준, stroke 방식으로 통일해서 어떤 크기로 써도 톤이 일관되게 함.

const base = {
  width: '1em',
  height: '1em',
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
};

export function IconBolt(props) {
  return (
    <svg {...base} {...props}>
      <path d="M13 2 4 14h6l-1 8 9-12h-6l1-8Z" />
    </svg>
  );
}

export function IconCards(props) {
  return (
    <svg {...base} {...props}>
      <rect x="3" y="6" width="12" height="15" rx="2" transform="rotate(-8 9 13.5)" />
      <rect x="8" y="4" width="12" height="15" rx="2" />
    </svg>
  );
}

export function IconCompass(props) {
  return (
    <svg {...base} {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="m15 9-2 6-6 2 2-6 6-2Z" />
    </svg>
  );
}

export function IconGift(props) {
  return (
    <svg {...base} {...props}>
      <rect x="3" y="9" width="18" height="12" rx="1.5" />
      <path d="M3 13h18M12 9v12" />
      <path d="M12 9c-1.5 0-4-1-4-3.2A2.3 2.3 0 0 1 10.3 3C12 3 12 6 12 9Zm0 0c1.5 0 4-1 4-3.2A2.3 2.3 0 0 0 13.7 3C12 3 12 6 12 9Z" />
    </svg>
  );
}

export function IconMap(props) {
  return (
    <svg {...base} {...props}>
      <path d="M9 4 3 6v14l6-2 6 2 6-2V4l-6 2-6-2Z" />
      <path d="M9 4v14M15 6v14" />
    </svg>
  );
}

export function IconPin(props) {
  return (
    <svg {...base} {...props}>
      <path d="M12 21s7-6.2 7-11.5A7 7 0 0 0 5 9.5C5 14.8 12 21 12 21Z" />
      <circle cx="12" cy="9.5" r="2.4" />
    </svg>
  );
}

export function IconCoin(props) {
  return (
    <svg {...base} {...props}>
      <circle cx="12" cy="12" r="9" />
      <path d="M9.5 15.2c.5.6 1.4 1 2.5 1 1.8 0 3-1 3-2.2s-1-1.7-3-2.2-3-1-3-2.2 1.2-2.1 3-2.1c1 0 1.9.4 2.4.9" />
      <path d="M12 7.3v9.4" />
    </svg>
  );
}

export function IconLock(props) {
  return (
    <svg {...base} {...props}>
      <rect x="4" y="10" width="16" height="10" rx="2" />
      <path d="M7 10V7a5 5 0 0 1 10 0v3" />
    </svg>
  );
}

export function IconAlert(props) {
  return (
    <svg {...base} {...props}>
      <path d="M12 3 2 20h20L12 3Z" />
      <path d="M12 10v4M12 17h.01" />
    </svg>
  );
}

export function IconArrowRight(props) {
  return (
    <svg {...base} {...props}>
      <path d="M5 12h14M13 6l6 6-6 6" />
    </svg>
  );
}

export function IconBike(props) {
  return (
    <svg {...base} {...props}>
      <circle cx="5.5" cy="17.5" r="3.5" />
      <circle cx="18.5" cy="17.5" r="3.5" />
      <path d="M5.5 17.5 10 8h5M10 8l3 9.5M13 17.5h5.5M9 6h2" />
    </svg>
  );
}

export function IconChevronLeft(props) {
  return (
    <svg {...base} {...props}>
      <path d="M15 6 9 12l6 6" />
    </svg>
  );
}
