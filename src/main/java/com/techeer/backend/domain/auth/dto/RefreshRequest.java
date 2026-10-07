package com.techeer.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "토큰 갱신 요청")
public record RefreshRequest(
        @Schema(description = "로그인 또는 이전 갱신 때 받은 refresh token")
        @NotBlank
        String refreshToken) {

    /**
     * 로그에 토큰이 찍히지 않도록 값을 가린다.
     */
    @Override
    public String toString() {
        return "RefreshRequest[refreshToken=****]";
    }
}
