package com.r2d.auth;

import java.time.Instant;

import com.r2d.common.R2dException;
import com.r2d.player.Player;
import com.r2d.player.PlayerService;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 요청에서 플레이어를 찾아냅니다.
 *
 * <p>우선순위:
 * <ol>
 *   <li>{@code Authorization: Bearer <accessToken>} — 정식 인증</li>
 *   <li>{@code X-Player-Id: <publicId>} — 레거시. 설정으로 끌 수 있습니다.</li>
 * </ol>
 *
 * <p>레거시 헤더는 <b>인증이 아닙니다.</b> 가명 식별자만 알면 누구든 그 플레이어로 행세할 수
 * 있습니다. 기존 프론트엔드와 심사 모드가 아직 이 방식을 쓰고 있어 전환 기간 동안만 열어 둡니다.
 */
@Component
public class CurrentPlayerArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;
    private final PlayerService playerService;
    private final AuthProperties properties;

    public CurrentPlayerArgumentResolver(TokenService tokenService, PlayerService playerService,
                                         AuthProperties properties) {
        this.tokenService = tokenService;
        this.playerService = playerService;
        this.properties = properties;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentPlayer.class)
                && Player.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        CurrentPlayer annotation = parameter.getParameterAnnotation(CurrentPlayer.class);
        boolean required = annotation == null || annotation.required();

        String authorization = webRequest.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            String publicId = tokenService.verifyAccessToken(
                    authorization.substring(BEARER_PREFIX.length()).trim(), Instant.now());
            return playerService.require(publicId);
        }

        if (properties.allowLegacyPlayerIdHeader()) {
            String legacy = webRequest.getHeader("X-Player-Id");
            if (legacy != null && !legacy.isBlank()) {
                return playerService.require(legacy);
            }
        }

        if (!required) {
            return null;
        }
        throw new R2dException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                "로그인이 필요합니다. Authorization 헤더에 액세스 토큰을 담아 주세요.");
    }
}
