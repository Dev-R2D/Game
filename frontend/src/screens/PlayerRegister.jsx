// 화면 1: 닉네임 등록
// 앱을 처음 켰을 때 나오는 화면. 닉네임만 입력하면 publicId를 받아서 저장함.

import { useState } from 'react';
import { registerPlayer, setPlayerId } from '../api';

// props로 onDone을 받음: 등록이 끝나면 부모(App.jsx)에게 "다음 화면으로 넘어가도 돼"라고 알려주는 함수
export default function PlayerRegister({ onDone }) {
  // useState는 "이 값이 바뀌면 화면을 다시 그려줘"라는 뜻의 React 문법이야.
  // nickname: 현재 입력창에 쓰여있는 값
  // setNickname: nickname 값을 바꿀 때 쓰는 함수
  const [nickname, setNickname] = useState('');

  // loading: 서버에 요청 보내는 중인지 (버튼 중복 클릭 방지용)
  const [loading, setLoading] = useState(false);

  // error: 실패했을 때 보여줄 메시지
  const [error, setError] = useState(null);

  // 폼 제출(버튼 클릭 또는 엔터) 시 실행되는 함수
  async function handleSubmit(e) {
    e.preventDefault(); // 페이지 새로고침되는 기본 동작 막기

    if (!nickname.trim()) {
      setError('닉네임을 입력해 주세요.');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      // api.js에 만들어둔 함수 호출 → 실제 백엔드로 POST /players 요청
      const data = await registerPlayer(nickname.trim());

      // 받은 publicId를 브라우저에 저장 (다음부터 이 값으로 계속 인증)
      setPlayerId(data.publicId);

      // 부모 컴포넌트한테 "등록 끝났어" 알려주기
      onDone(data);
    } catch (err) {
      // 서버가 NICKNAME_TAKEN 같은 에러를 주면 여기서 잡힘
      if (err.code === 'NICKNAME_TAKEN') {
        setError('이미 사용 중인 닉네임이에요. 다른 이름을 입력해 주세요.');
      } else {
        setError(err.message || '등록에 실패했어요. 잠시 후 다시 시도해 주세요.');
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="screen">
      <div className="eyebrow">R2D · 라이더 등록</div>
      <h1>어떻게 불러드릴까요?</h1>
      <p className="subtitle">
        비밀번호는 필요 없어요. 닉네임만 정하면 바로 시작할 수 있습니다.
        기본적으로 다른 라이더에게는 공개되지 않아요.
      </p>

      <form onSubmit={handleSubmit}>
        <div className="field">
          <label htmlFor="nickname">닉네임</label>
          <input
            id="nickname"
            type="text"
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            placeholder="예: 동탄라이더"
            maxLength={20}
            disabled={loading}
            autoFocus
          />
        </div>

        {error && <div className="error-box">{error}</div>}

        <button type="submit" className="btn-primary" disabled={loading}>
          {loading ? '등록 중...' : '시작하기'}
        </button>

        <p className="hint">
          닉네임 공개 여부는 나중에 프로필 설정에서 언제든 바꿀 수 있어요.
        </p>
      </form>
    </div>
  );
}
