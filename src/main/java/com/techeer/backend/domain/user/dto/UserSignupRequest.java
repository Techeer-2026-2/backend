package com.techeer.backend.domain.user.dto;

import com.techeer.backend.global.validation.MaxBytes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
        String nickname,

        @Schema(description = "연령대 (선택). 10s, 20s, 30s, 40s, 50s 중 하나. 비우면 배너가 노출되지 않는다.",
                example = "20s")
        @Pattern(regexp = AGE_GROUP_PATTERN)
        String ageGroup,

        @Schema(description = "성별 (선택). male, female 중 하나.", example = "female")
        @Pattern(regexp = GENDER_PATTERN)
        String gender) {

    /** 캠페인 타겟 연령대(CampaignTargetRules.AGE_GROUPS)와 같은 값만 받는다. */
    public static final String AGE_GROUP_PATTERN = "10s|20s|30s|40s|50s";

    /** 회원 성별. 캠페인 쪽의 all 은 "성별 제한 없음"이라 회원 값으로는 받지 않는다. */
    public static final String GENDER_PATTERN = "male|female";

    /**
     * 로그에 비밀번호가 찍히지 않도록 password 를 가린다.
     */
    @Override
    public String toString() {
        return "UserSignupRequest[email=" + email + ", password=****, nickname=" + nickname
                + ", ageGroup=" + ageGroup + ", gender=" + gender + "]";
    }
}
