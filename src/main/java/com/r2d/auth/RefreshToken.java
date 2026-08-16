package com.r2d.auth;

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
 * 리프레시 토큰.
 *
 * <p>액세스 토큰은 짧게(1시간) 두고, 앱이 계속 로그인 상태를 유지하는 것은 이 토큰이 맡습니다.
 *
 * <p><b>원문을 저장하지 않습니다.</b> DB가 유출돼도 토큰을 그대로 쓰지 못하도록 SHA-256
 * 해시만 보관합니다. 액세스 토큰과 달리 리프레시 토큰은 서버가 즉시 무효화할 수 있어야 하므로
 * (로그아웃·비밀번호 변경) JWT가 아니라 이렇게 DB에 둡니다.
 */
@Entity
@Table(name = "refresh_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_refresh_hash", columnNames = {"tokenHash"}),
        indexes = @Index(name = "idx_refresh_account", columnList = "accountId"))
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** 사용되어 회전된 시각. 값이 있으면 더는 쓸 수 없습니다. */
    private Instant rotatedAt;

    /** 로그아웃 등으로 강제 폐기된 시각. */
    private Instant revokedAt;

    protected RefreshToken() {
    }

    public RefreshToken(String tokenHash, Long accountId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.accountId = accountId;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return rotatedAt == null && revokedAt == null && now.isBefore(expiresAt);
    }

    public void rotate(Instant now) {
        this.rotatedAt = now;
    }

    public void revoke(Instant now) {
        if (this.revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRotatedAt() {
        return rotatedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
