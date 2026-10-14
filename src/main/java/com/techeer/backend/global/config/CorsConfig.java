package com.techeer.backend.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 브라우저(프론트)에서 오는 요청을 허용한다 (CORS).
 *
 * <p>허용 출처(origin)는 {@code cors.allowed-origins} 에 쉼표로 나열한 주소뿐이다. 와일드카드({@code *})는 쓰지 않는다.
 * 인증 정보는 쿠키가 아니라 헤더(Authorization, X-Member-Id)로 보내므로 {@code allowCredentials} 는 켜지 않는다.
 *
 * <p>다른 웹 설정(WebConfig, AuthWebConfig)과 충돌하지 않도록 CORS 전용 설정 클래스로 따로 뒀다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization", "X-Member-Id")
                .maxAge(3600);
    }
}
