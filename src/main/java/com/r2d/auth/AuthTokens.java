package com.r2d.auth;

/**
 * 로그인·갱신 결과로 내려가는 토큰 묶음.
 *
 * <p>{@code refreshToken} 원문은 이 순간에만 존재합니다. 서버는 해시만 보관하므로 다시
 * 조회할 수 없고, 클라이언트가 안전한 저장소에 보관해야 합니다.
 */
public record AuthTokens(
        String accessToken,
        String refreshToken,
        int expiresInSeconds,
        String tokenType,
        /** 게임 API에서 쓰는 플레이어 가명 식별자. */
        String playerPublicId,
        String nickname
) {

    public static AuthTokens of(String accessToken, String refreshToken, int expiresInSeconds,
                                String playerPublicId, String nickname) {
        return new AuthTokens(accessToken, refreshToken, expiresInSeconds, "Bearer", playerPublicId, nickname);
    }
}
