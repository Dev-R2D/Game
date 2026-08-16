package com.r2d.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 요청을 보낸 플레이어를 컨트롤러 인자로 주입합니다.
 *
 * <pre>
 * public RideResponse start(&#64;CurrentPlayer Player player, ...)
 * </pre>
 *
 * <p>인증 방식(Bearer 토큰 / 레거시 헤더)이 바뀌어도 컨트롤러는 손대지 않도록,
 * 해석은 {@link CurrentPlayerArgumentResolver} 한 곳에만 둡니다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentPlayer {

    /** false면 인증되지 않은 요청에서 null이 주입됩니다(선택적 인증 엔드포인트용). */
    boolean required() default true;
}
