package com.techeer.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "발급된 토큰")
public record TokenResponse(
        @Schema(description = "API 호출에 쓰는 access token (짧게 유효)") String accessToken,
        @Schema(description = "access token 재발급에 쓰는 refresh token (길게 유효)") String refreshToken,
        @Schema(description = "Authorization 헤더에 붙이는 방식", example = "Bearer") String tokenType,
        @Schema(description = "access token 유효 시간(초)", example = "1800") long expiresIn) {

    private static final String BEARER = "Bearer";

    public static TokenResponse of(String accessToken, String refreshToken, long expiresIn) {
        return new TokenResponse(accessToken, refreshToken, BEARER, expiresIn);
    }
}
