package com.r2d.account;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 로그인 계정. 게임 플레이어({@code Player})와 1:1로 연결됩니다.
 *
 * <p>대입 공격 방어를 위해 실패 횟수를 세고, 임계치를 넘으면 일정 시간 잠급니다.
 * {@link com.r2d.auth.AuthService}와 {@link com.r2d.auth.AuthSideEffects}가 이 엔티티를 씁니다.
 */
@Entity
@Table(name = "accounts",
        uniqueConstraints = @UniqueConstraint(name = "uk_account_email", columnNames = {"email"}),
        indexes = @Index(name = "idx_account_player", columnList = "playerId"))
public class Account {

    private static final int MAX_FAILURES = 10;
    private static final long LOCK_MINUTES = 15;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private Long playerId;

    @Column(nullable = false)
    private int failedAttempts = 0;

    private Instant lockedUntil;

    /** 이메일 인증 여부. 인증 메일 발송이 아직 없어서(README 참고) 항상 false로 시작함. */
    @Column(nullable = false)
    private boolean emailVerified = false;

    private Instant lastLoginAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Account() {
    }

    public Account(String email, String passwordHash, Long playerId) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.playerId = playerId;
    }

    /** 이메일은 대소문자·앞뒤 공백만 정리해서 비교·저장함(도메인 별칭 등 복잡한 정규화는 하지 않음). */
    public static String normalizeEmail(String raw) {
        return raw == null ? null : raw.trim().toLowerCase();
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** 로그인 성공 — 실패 카운트와 잠금을 모두 풀고 마지막 로그인 시각을 남깁니다. */
    public void recordSuccess(Instant now) {
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = now;
    }

    /** 로그인 실패 — 임계치를 넘으면 잠급니다. */
    public void recordFailure(Instant now) {
        this.failedAttempts += 1;
        if (this.failedAttempts >= MAX_FAILURES) {
            this.lockedUntil = now.plusSeconds(LOCK_MINUTES * 60);
        }
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.failedAttempts = 0;
        this.lockedUntil = null;
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

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
