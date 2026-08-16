package com.r2d.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 인증 설정.
 *
 * <p>{@code application.yml}의 {@code r2d.auth.*}로 덮어씁니다.
 */
@ConfigurationProperties(prefix = "r2d.auth")
public record AuthProperties(
        /**
         * 액세스 토큰 서명 키(HS256). 최소 32바이트.
         *
         * <p>비워 두면 기동할 때마다 무작위로 만들고 경고를 남깁니다. 알려진 기본키를 코드에
         * 박아 두면 그대로 배포됐을 때 누구나 토큰을 위조할 수 있으므로, 안전한 쪽을 기본값으로 둡니다.
         * 서버를 여러 대 띄우거나 재시작 후에도 로그인을 유지하려면 반드시 값을 지정해야 합니다.
         */
        String jwtSecret,

        /** 액세스 토큰 유효 시간(분). */
        @DefaultValue("60") int accessTokenMinutes,

        /** 리프레시 토큰 유효 시간(일). */
        @DefaultValue("30") int refreshTokenDays,

        /** 토큰 발급자 표기. */
        @DefaultValue("r2d") String issuer,

        /**
         * 레거시 {@code X-Player-Id} 헤더 인증 허용 여부.
         *
         * <p>이 헤더는 가명 식별자만 알면 누구나 그 플레이어 행세를 할 수 있어 인증이 아닙니다.
         * 기존 프론트엔드와 심사 모드가 아직 이 방식을 쓰고 있어 기본값을 켜 두었을 뿐,
         * 실제 서비스에서는 반드시 꺼야 합니다.
         */
        @DefaultValue("true") boolean allowLegacyPlayerIdHeader,

        /** 비밀번호 최소 길이. */
        @DefaultValue("8") int minPasswordLength
) {
}
