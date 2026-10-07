package com.techeer.backend.domain.advertiser.dto;

import com.techeer.backend.global.validation.MaxBytes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "광고주 회원가입 요청")
public record SignupRequest(
        @Schema(description = "로그인에 쓸 이메일 (대소문자는 구분하지 않는다)", example = "owner@techeer.com")
        @NotBlank
        @Email
        @Size(max = 128)
        String email,

        // bcrypt 는 72바이트를 넘으면 예외를 던진다. 글자 수(@Size)가 아니라 바이트로 막아야 한글 비밀번호도 안전하다.
        // 영문+숫자 조합 같은 강한 규칙은 Phase 3 이후에 추가한다.
        @Schema(description = "비밀번호 (8자 이상, UTF-8 기준 72바이트 이하: 영문 72자 / 한글 24자까지)", example = "password1234")
        @NotBlank
        @Size(min = 8)
        @MaxBytes(72)
        String password,

        @Schema(description = "사업자명", example = "테커 카페")
        @NotBlank
        @Size(max = 128)
        String businessName) {

    /**
     * 로그에 비밀번호가 찍히지 않도록 password 를 가린다.
     */
    @Override
    public String toString() {
        return "SignupRequest[email=" + email + ", password=****, businessName=" + businessName + "]";
    }
}
