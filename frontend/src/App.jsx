// App.jsx = 전체 앱의 뼈대.
// "지금 어떤 화면을 보여줄지"를 여기서 관리해.
//
// 정식 라우터(react-router) 없이, screen이라는 state 값 하나로
// "지금 어느 화면인지"를 표현하는 방식을 그대로 이어감.
// 화면 순서: onboarding → register → missions → deck → ride → settlement → pack → map

import { useState } from 'react';
import { getPlayerId } from './api';
import Onboarding from './screens/Onboarding';
import PlayerRegister from './screens/PlayerRegister';
import MissionSelect from './screens/MissionSelect';
import DeckBuilder from './screens/DeckBuilder';
import Settlement from './screens/Settlement';
import PackOpen from './screens/PackOpen';
import MapView from './screens/MapView';

function App() {
  // 앱을 처음 켰을 때, 이미 저장된 publicId가 있으면 온보딩·등록 화면을 건너뜀
  const [screen, setScreen] = useState(getPlayerId() ? 'missions' : 'onboarding');
  const [player, setPlayer] = useState(null);

  // 화면 사이에서 들고 다니는 값들
  const [regionCode, setRegionCode] = useState('DONGTAN2');
  const [deckCardCodes, setDeckCardCodes] = useState([]);
  const [rideId, setRideId] = useState(null);
  const [clientEstimatedDamage, setClientEstimatedDamage] = useState(null);
  const [rideRoute, setRideRoute] = useState(null); // 방금 달린 경로(정산 화면 결과 지도용)

  function handleRegisterDone(data) {
    setPlayer(data);
    setScreen('missions');
  }

  function handleMissionNext(code) {
    setRegionCode(code);
    setScreen('deck');
  }

  function handleDeckNext(code, cardCodes) {
    setRegionCode(code);
    setDeckCardCodes(cardCodes);
    setScreen('ride');
  }

  function handleRideFinish(id, estimatedDamage, route) {
    setRideId(id);
    setClientEstimatedDamage(estimatedDamage || null);
    setRideRoute(route || null);
    setScreen('settlement');
  }

  switch (screen) {
    case 'onboarding':
      return <Onboarding onDone={() => setScreen('register')} />;
    case 'register':
      return <PlayerRegister onDone={handleRegisterDone} />;
    case 'missions':
      return <MissionSelect onNext={handleMissionNext} onNavigate={setScreen} />;
    case 'deck':
      return (
        <DeckBuilder
          regionCode={regionCode}
          onBack={() => setScreen('missions')}
          onNext={handleDeckNext}
          onNavigate={setScreen}
        />
      );
    case 'settlement':
      return (
        <Settlement
          rideId={rideId}
          clientEstimatedDamage={clientEstimatedDamage}
          route={rideRoute}
          onNext={() => setScreen('pack')}
        />
      );
    case 'pack':
      return <PackOpen onNext={() => setScreen('map')} onNavigate={setScreen} />;
    case 'map':
      return <MapView onBack={() => setScreen('missions')} onNavigate={setScreen} />;
    default:
      return <MissionSelect onNext={handleMissionNext} onNavigate={setScreen} />;
  }
}

export default App;
