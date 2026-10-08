package com.techeer.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "광고주 로그인 요청")
public record LoginRequest(
        @Schema(description = "가입한 이메일", example = "owner@techeer.com")
        @NotBlank
        String email,

        @Schema(description = "비밀번호", example = "password1234")
        @NotBlank
        String password) {

    /**
     * 로그에 비밀번호가 찍히지 않도록 password 를 가린다.
     */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=****]";
    }
}
