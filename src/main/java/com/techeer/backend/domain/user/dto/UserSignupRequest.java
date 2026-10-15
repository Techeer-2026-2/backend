package com.techeer.backend.domain.user.dto;

import com.techeer.backend.global.validation.MaxBytes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "음악 앱 회원가입 요청")
public record UserSignupRequest(
        @Schema(description = "로그인에 쓸 이메일 (대소문자는 구분하지 않는다)", example = "listener@techeer.com")
        @NotBlank
        @Email
        @Size(max = 128)
        String email,

        // bcrypt 는 72바이트를 넘으면 예외를 던진다. 글자 수(@Size)가 아니라 바이트로 막아야 한글 비밀번호도 안전하다.
        @Schema(description = "비밀번호 (8자 이상, UTF-8 기준 72바이트 이하: 영문 72자 / 한글 24자까지)", example = "password1234")
        @NotBlank
        @Size(min = 8)
        @MaxBytes(72)
        String password,

        @Schema(description = "닉네임", example = "출근러")
        @NotBlank
        @Size(max = 50)
        String nickname) {

    /**
     * 로그에 비밀번호가 찍히지 않도록 password 를 가린다.
     */
    @Override
    public String toString() {
        return "UserSignupRequest[email=" + email + ", password=****, nickname=" + nickname + "]";
    }
}
