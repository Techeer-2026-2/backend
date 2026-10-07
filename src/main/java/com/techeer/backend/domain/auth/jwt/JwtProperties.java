package com.techeer.backend.domain.auth.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 jwt.* 설정값. secret 은 서명 키(32바이트 이상)이다.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long accessTokenMinutes, long refreshTokenDays) {
}
