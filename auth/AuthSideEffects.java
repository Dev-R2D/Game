package com.r2d.auth;

import java.time.Instant;

import com.r2d.account.AccountRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실패 경로에서도 <b>반드시 남아야 하는</b> 기록.
 *
 * <p>로그인 실패 횟수와 토큰 폐기는 예외를 던지기 직전에 일어납니다. 그런데 예외가 나가면
 * 호출한 트랜잭션은 롤백되고, 같은 트랜잭션에서 쓴 내용은 함께 사라집니다. 그러면
 * 대입 공격 잠금은 영원히 카운트가 0이고, 탈취된 리프레시 토큰도 폐기되지 않습니다.
 *
 * <p>그래서 이 두 가지는 <b>별도 트랜잭션</b>({@link Propagation#REQUIRES_NEW})에서 커밋합니다.
 * 자기 호출(self-invocation)은 프록시를 타지 않으므로 반드시 별도 빈이어야 합니다.
 */
@Service
public class AuthSideEffects {

    private final AccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthSideEffects(AccountRepository accountRepository,
                           RefreshTokenRepository refreshTokenRepository) {
        this.accountRepository = accountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /** 비밀번호 실패를 계정에 누적합니다. 호출한 트랜잭션이 롤백돼도 남습니다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginFailure(Long accountId, Instant now) {
        accountRepository.findById(accountId).ifPresent(account -> account.recordFailure(now));
    }

    /** 계정의 살아 있는 리프레시 토큰을 모두 폐기합니다. 호출한 트랜잭션이 롤백돼도 남습니다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllRefreshTokens(Long accountId, Instant now) {
        refreshTokenRepository.findByAccountIdAndRevokedAtIsNullAndRotatedAtIsNull(accountId)
                .forEach(token -> token.revoke(now));
    }

}
