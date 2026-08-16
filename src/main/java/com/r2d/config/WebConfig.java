package com.r2d.config;

import java.util.List;

import com.r2d.auth.CurrentPlayerArgumentResolver;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 웹 계층 설정.
 *
 * <p>프론트엔드는 개발 중 다른 포트(Vite 5173, CRA 3000 등)에서 뜨므로 CORS 허용이 필요합니다.
 * 안드로이드 앱은 브라우저가 아니라 CORS 영향을 받지 않지만, 웹 대시보드와 로컬 개발을 위해 엽니다.
 *
 * <p>허용 출처는 {@code application.yml}의 {@code r2d.web.allowed-origins}로 바꿀 수 있습니다.
 * 운영 배포 시에는 반드시 실제 도메인만 남기고 좁혀야 합니다.
 */
@Configuration
@ConfigurationProperties(prefix = "r2d.web")
public class WebConfig implements WebMvcConfigurer {

    private List<String> allowedOrigins = List.of(
            "http://localhost:3000",
            "http://localhost:5173",
            "http://localhost:8081",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:5173"
    );

    private final CurrentPlayerArgumentResolver currentPlayerArgumentResolver;

    public WebConfig(CurrentPlayerArgumentResolver currentPlayerArgumentResolver) {
        this.currentPlayerArgumentResolver = currentPlayerArgumentResolver;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                // 커스텀 헤더는 명시적으로 허용해야 프리플라이트를 통과합니다.
                .allowedHeaders("Content-Type", "Authorization", "X-Player-Id")
                .maxAge(3600);
    }

    /** {@code @CurrentPlayer} 파라미터를 해석할 수 있게 등록합니다. */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentPlayerArgumentResolver);
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
