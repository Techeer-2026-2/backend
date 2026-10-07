package com.techeer.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "로그아웃 요청")
public record LogoutRequest(
        @Schema(description = "폐기할 refresh token")
        @NotBlank
        String refreshToken) {

    /**
     * 로그에 토큰이 찍히지 않도록 값을 가린다.
     */
    @Override
    public String toString() {
        return "LogoutRequest[refreshToken=****]";
    }
}
