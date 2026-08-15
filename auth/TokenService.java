package com.r2d.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.r2d.common.R2dException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 액세스 토큰(JWT) 발급·검증과 리프레시 토큰 생성.
 *
 * <p>액세스 토큰에는 플레이어의 가명 식별자만 담습니다. 이메일이나 계정 ID는 넣지 않습니다 —
 * JWT는 서명만 되어 있고 암호화되지 않아 누구나 내용을 읽을 수 있기 때문입니다.
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    /** HS256이 요구하는 최소 키 길이(바이트). */
    private static final int MIN_SECRET_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] secret;
    private final AuthProperties properties;

    public TokenService(AuthProperties properties) {
        this.properties = properties;
        this.secret = resolveSecret(properties.jwtSecret());
    }

    private static byte[] resolveSecret(String configured) {
        if (configured != null && !configured.isBlank()) {
            byte[] bytes = configured.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                        "r2d.auth.jwt-secret은 최소 " + MIN_SECRET_BYTES + "바이트여야 합니다. 현재 "
                                + bytes.length + "바이트입니다.");
            }
            return bytes;
        }
        byte[] generated = new byte[MIN_SECRET_BYTES];
        RANDOM.nextBytes(generated);
        log.warn("r2d.auth.jwt-secret이 설정되지 않아 임시 키를 생성했습니다. "
                + "서버를 재시작하면 발급된 모든 토큰이 무효가 되고, 여러 대로 확장할 수 없습니다. "
                + "운영 환경에서는 반드시 값을 지정하세요.");
        return generated;
    }

    /** 플레이어 가명 식별자로 액세스 토큰을 발급합니다. */
    public String issueAccessToken(String playerPublicId, Instant now) {
        Instant expiry = now.plus(properties.accessTokenMinutes(), ChronoUnit.MINUTES);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(playerPublicId)
                .issuer(properties.issuer())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiry))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(secret));
        } catch (JOSEException e) {
            throw new IllegalStateException("액세스 토큰 서명에 실패했습니다.", e);
        }
        return jwt.serialize();
    }

    /**
     * 액세스 토큰을 검증하고 플레이어 가명 식별자를 돌려줍니다.
     *
     * <p>서명뿐 아니라 알고리즘·발급자·만료를 모두 확인합니다. 알고리즘을 확인하지 않으면
     * 공격자가 {@code alg: none}으로 바꾼 토큰을 밀어 넣을 수 있습니다.
     */
    public String verifyAccessToken(String token, Instant now) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);

            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
                throw unauthorized("지원하지 않는 토큰 알고리즘입니다.");
            }
            if (!jwt.verify(new MACVerifier(secret))) {
                throw unauthorized("토큰 서명이 올바르지 않습니다.");
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!properties.issuer().equals(claims.getIssuer())) {
                throw unauthorized("토큰 발급자가 올바르지 않습니다.");
            }
            Date expiry = claims.getExpirationTime();
            if (expiry == null || !now.isBefore(expiry.toInstant())) {
                throw unauthorized("토큰이 만료되었습니다. 다시 로그인하거나 토큰을 갱신해 주세요.");
            }
            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                throw unauthorized("토큰에 사용자 정보가 없습니다.");
            }
            return subject;
        } catch (R2dException e) {
            throw e;
        } catch (java.text.ParseException | JOSEException e) {
            throw unauthorized("토큰을 해석할 수 없습니다.");
        }
    }

    /** 리프레시 토큰 원문. 이 값은 발급 시점에 딱 한 번만 클라이언트에 전달됩니다. */
    public String generateRefreshTokenValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 리프레시 토큰 저장·조회용 해시. 원문은 DB에 남기지 않습니다. */
    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    public Instant refreshTokenExpiry(Instant now) {
        return now.plus(properties.refreshTokenDays(), ChronoUnit.DAYS);
    }

    public int accessTokenSeconds() {
        return properties.accessTokenMinutes() * 60;
    }

    private static R2dException unauthorized(String message) {
        return new R2dException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }
}
