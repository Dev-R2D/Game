package com.r2d.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

import com.r2d.account.Account;
import com.r2d.account.AccountRepository;
import com.r2d.common.R2dException;
import com.r2d.player.Player;
import com.r2d.player.PlayerRepository;
import com.r2d.player.PlayerService;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 로그인.
 *
 * <p>가입하면 계정({@link Account})과 게임 플레이어({@link Player})가 함께 만들어지고,
 * 이후 게임 API는 플레이어의 가명 식별자만 사용합니다.
 */
@Service
public class AuthService {

    /** 형식 검사용. 완벽한 이메일 정규식은 존재하지 않으므로 명백한 오타만 걸러냅니다. */
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+\\.[^@\\s]+$");

    private final AccountRepository accountRepository;
    private final PlayerRepository playerRepository;
    private final PlayerService playerService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuthProperties properties;
    private final AuthSideEffects sideEffects;

    public AuthService(AccountRepository accountRepository,
                       PlayerRepository playerRepository,
                       PlayerService playerService,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       AuthProperties properties,
                       AuthSideEffects sideEffects) {
        this.accountRepository = accountRepository;
        this.playerRepository = playerRepository;
        this.playerService = playerService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.properties = properties;
        this.sideEffects = sideEffects;
    }

    /** 회원가입. 계정과 플레이어를 함께 만들고 바로 로그인 상태로 만듭니다. */
    @Transactional
    public AuthTokens signUp(String rawEmail, String rawPassword, String nickname) {
        String email = requireValidEmail(rawEmail);
        requireStrongEnough(rawPassword);

        if (accountRepository.existsByEmail(email)) {
            throw R2dException.conflict("EMAIL_TAKEN", "이미 가입된 이메일입니다.");
        }

        // 닉네임 중복 검사는 PlayerService가 담당합니다. 계정보다 먼저 만들어 실패 시 계정이 남지 않게 합니다.
        Player player = playerService.register(nickname);
        Account account = accountRepository.save(
                new Account(email, passwordEncoder.encode(rawPassword), player.getId()));

        return issueFor(account, player, Instant.now());
    }

    /**
     * 로그인.
     *
     * <p>이메일이 없든 비밀번호가 틀리든 <b>같은 메시지</b>를 돌려줍니다. 둘을 구분해 알려주면
     * 공격자가 "가입된 이메일 목록"을 만들 수 있기 때문입니다.
     */
    @Transactional
    public AuthTokens logIn(String rawEmail, String rawPassword) {
        Instant now = Instant.now();
        String email = Account.normalizeEmail(rawEmail);
        Optional<Account> found = email == null ? Optional.empty() : accountRepository.findByEmail(email);

        if (found.isEmpty()) {
            // 존재하지 않는 계정도 해시 검증과 비슷한 시간을 쓰게 해서, 응답 속도로 가입 여부를
            // 알아내지 못하게 합니다.
            passwordEncoder.encode(rawPassword == null ? "" : rawPassword);
            throw invalidCredentials();
        }

        Account account = found.get();
        if (account.isLocked(now)) {
            throw new R2dException(HttpStatus.TOO_MANY_REQUESTS, "ACCOUNT_LOCKED",
                    "로그인 시도가 많아 잠시 잠겼습니다. 잠시 후 다시 시도해 주세요.");
        }
        if (rawPassword == null || !passwordEncoder.matches(rawPassword, account.getPasswordHash())) {
            // 예외로 이 트랜잭션은 롤백되므로, 실패 기록은 별도 트랜잭션에서 커밋해야 남습니다.
            sideEffects.recordLoginFailure(account.getId(), now);
            throw invalidCredentials();
        }

        account.recordSuccess(now);
        Player player = playerRepository.findById(account.getPlayerId())
                .orElseThrow(() -> R2dException.notFound("PLAYER_NOT_FOUND", "연결된 플레이어를 찾을 수 없습니다."));
        return issueFor(account, player, now);
    }

    /**
     * 리프레시 토큰으로 재발급.
     *
     * <p>쓴 토큰은 즉시 회전시켜 재사용을 막습니다. 이미 회전된 토큰이 다시 들어오면 탈취를
     * 의심할 수 있는 신호이므로, 해당 계정의 살아 있는 토큰을 모두 폐기하고 재로그인을 요구합니다.
     */
    @Transactional
    public AuthTokens refresh(String rawRefreshToken) {
        Instant now = Instant.now();
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw invalidRefresh();
        }

        RefreshToken stored = refreshTokenRepository
                .findByTokenHash(tokenService.hashRefreshToken(rawRefreshToken))
                .orElseThrow(AuthService::invalidRefresh);

        if (stored.getRotatedAt() != null) {
            // 마찬가지로 별도 트랜잭션에서 폐기해야 예외 이후에도 남습니다.
            sideEffects.revokeAllRefreshTokens(stored.getAccountId(), now);
            throw new R2dException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSED",
                    "이미 사용된 토큰입니다. 보안을 위해 모든 세션을 종료했습니다. 다시 로그인해 주세요.");
        }
        if (!stored.isUsable(now)) {
            throw invalidRefresh();
        }

        stored.rotate(now);
        Account account = accountRepository.findById(stored.getAccountId())
                .orElseThrow(AuthService::invalidRefresh);
        Player player = playerRepository.findById(account.getPlayerId())
                .orElseThrow(() -> R2dException.notFound("PLAYER_NOT_FOUND", "연결된 플레이어를 찾을 수 없습니다."));

        return issueFor(account, player, now);
    }

    /** 로그아웃. 해당 리프레시 토큰만 폐기합니다(다른 기기의 세션은 유지). */
    @Transactional
    public void logOut(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(tokenService.hashRefreshToken(rawRefreshToken))
                .ifPresent(token -> token.revoke(Instant.now()));
    }

    /** 비밀번호 변경. 변경 즉시 모든 기기의 세션을 끊습니다. */
    @Transactional
    public void changePassword(Player player, String currentPassword, String newPassword) {
        Account account = accountRepository.findByPlayerId(player.getId())
                .orElseThrow(() -> R2dException.badRequest("NO_ACCOUNT",
                        "이 플레이어에는 로그인 계정이 연결되어 있지 않습니다."));

        if (currentPassword == null || !passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw invalidCredentials();
        }
        requireStrongEnough(newPassword);

        account.changePassword(passwordEncoder.encode(newPassword));
        revokeAll(account.getId(), Instant.now());
    }

    /** 플레이어에 연결된 계정 정보(있으면). */
    public Optional<Account> accountOf(Player player) {
        return accountRepository.findByPlayerId(player.getId());
    }

    private AuthTokens issueFor(Account account, Player player, Instant now) {
        String accessToken = tokenService.issueAccessToken(player.getPublicId(), now);
        String rawRefresh = tokenService.generateRefreshTokenValue();
        refreshTokenRepository.save(new RefreshToken(
                tokenService.hashRefreshToken(rawRefresh), account.getId(), tokenService.refreshTokenExpiry(now)));

        return AuthTokens.of(accessToken, rawRefresh, tokenService.accessTokenSeconds(),
                player.getPublicId(), player.getNickname());
    }

    private void revokeAll(Long accountId, Instant now) {
        refreshTokenRepository.findByAccountIdAndRevokedAtIsNullAndRotatedAtIsNull(accountId)
                .forEach(token -> token.revoke(now));
    }

    private String requireValidEmail(String raw) {
        String email = Account.normalizeEmail(raw);
        if (email == null || !EMAIL.matcher(email).matches() || email.length() > 254) {
            throw R2dException.badRequest("INVALID_EMAIL", "이메일 형식이 올바르지 않습니다.");
        }
        return email;
    }

    private void requireStrongEnough(String password) {
        if (password == null || password.length() < properties.minPasswordLength()) {
            throw R2dException.badRequest("WEAK_PASSWORD",
                    "비밀번호는 " + properties.minPasswordLength() + "자 이상이어야 합니다.");
        }
        if (password.length() > 100) {
            // BCrypt는 72바이트 이후를 무시하므로 지나치게 긴 입력은 받지 않습니다.
            throw R2dException.badRequest("PASSWORD_TOO_LONG", "비밀번호가 너무 깁니다.");
        }
    }

    private static R2dException invalidCredentials() {
        return new R2dException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    private static R2dException invalidRefresh() {
        return new R2dException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                "토큰이 유효하지 않습니다. 다시 로그인해 주세요.");
    }
}
