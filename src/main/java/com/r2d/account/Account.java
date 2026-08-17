package com.r2d.account;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 로그인 계정.
 *
 * <p><b>왜 Player와 분리했는가.</b> 개인정보 최소화 원칙상 정산·기여·랭킹 같은 게임 로직은
 * 가명 식별자만 봐야 합니다. 이메일을 {@code Player}에 넣으면 게임 테이블 전체가 개인정보를
 * 들고 다니게 됩니다. 그래서 신원(이메일·비밀번호)은 이 테이블에만 두고, 게임 쪽은
 * {@code playerId}만 참조합니다. 계정을 지워도 게임 데이터는 가명 상태로 남길 수 있습니다.
 */
@Entity
@Table(name = "accounts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_account_email", columnNames = {"email"}),
        @UniqueConstraint(name = "uk_account_player", columnNames = {"playerId"})
})
public class Account {

    /** 로그인 연속 실패가 이 횟수를 넘으면 잠급니다. */
    private static final int MAX_FAILED_ATTEMPTS = 10;

    /** 잠금 지속 시간(분). */
    private static final int LOCK_MINUTES = 15;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소문자로 정규화해 저장합니다. 대소문자만 다른 중복 가입을 막기 위해서입니다. */
    @Column(nullable = false, length = 254)
    private String email;

    /** BCrypt 해시. 평문은 어디에도 저장하지 않습니다. */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    /** 이 계정이 소유한 게임 플레이어. */
    @Column(nullable = false)
    private Long playerId;

    /**
     * 이메일 소유 확인 여부.
     *
     * <p>현재 빌드는 인증 메일을 발송하지 않으므로 가입 즉시 사용 가능합니다. 발송을 켜면
     * 이 필드로 로그인을 제한하면 되도록 자리만 만들어 두었습니다.
     */
    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column(nullable = false)
    private int failedAttempts = 0;

    private Instant lockedUntil;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant lastLoginAt;

    protected Account() {
    }

    public Account(String email, String passwordHash, Long playerId) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.playerId = playerId;
    }


    /** 이메일 정규화. 저장·조회 양쪽에서 반드시 같은 함수를 거쳐야 합니다. */
    public static String normalizeEmail(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** 지금 로그인 시도를 받아도 되는 상태인지. */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    /**
     * 비밀번호가 틀렸을 때 호출합니다.
     *
     * <p>무제한 시도를 허용하면 약한 비밀번호가 시간문제로 뚫립니다. 계정을 영구 잠그지 않고
     * 시간 잠금만 거는 이유는, 남의 계정을 일부러 잠가 버리는 공격을 막기 위해서입니다.
     */
    public void recordFailure(Instant now) {
        this.failedAttempts++;
        if (this.failedAttempts >= MAX_FAILED_ATTEMPTS) {
            this.lockedUntil = now.plus(LOCK_MINUTES, ChronoUnit.MINUTES);
            this.failedAttempts = 0;
        }
    }

    public void recordSuccess(Instant now) {
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = now;
    }

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
    }

    public void markEmailVerified() {
        this.emailVerified = true;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
