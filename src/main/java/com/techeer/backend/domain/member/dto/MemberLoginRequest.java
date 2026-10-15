package com.techeer.backend.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "음악 앱 로그인 요청")
public record MemberLoginRequest(
        @Schema(description = "가입한 이메일", example = "listener@techeer.com")
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
        return "MemberLoginRequest[email=" + email + ", password=****]";
    }
}
