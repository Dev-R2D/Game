// 온보딩 이후 화면들(미션/덱/지도/팩)에서 공통으로 쓰는 하단 네비게이션

import { IconBolt, IconCards, IconMap, IconGift } from './Icons';

const NAV_ITEMS = [
  { screen: 'missions', Icon: IconBolt, label: '미션' },
  { screen: 'deck', Icon: IconCards, label: '덱' },
  { screen: 'map', Icon: IconMap, label: '지도' },
  { screen: 'pack', Icon: IconGift, label: '팩' },
];

export default function BottomNav({ screen, onNavigate }) {
  return (
    <div className="bottom-nav">
      {NAV_ITEMS.map(({ screen: s, Icon, label }) => (
        <button
          key={s}
          className={screen === s ? 'active' : ''}
          onClick={() => onNavigate(s)}
        >
          <span className="nav-icon"><Icon /></span>
          {label}
        </button>
      ))}
    </div>
  );
}
