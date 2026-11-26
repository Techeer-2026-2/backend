package com.techeer.backend.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 내 정보 수정 요청. 닉네임·연령대·성별을 바꿀 수 있고, 보내지 않은(null) 값은 그대로 둔다.
 * 이메일·비밀번호는 이 요청으로 바꿀 수 없도록 필드 자체를 두지 않았다.
 */
@Schema(description = "음악 앱 회원 내 정보 수정 요청. 보내지 않은 값은 바뀌지 않는다.")
public record UpdateUserRequest(
        @Schema(description = "새 닉네임 (보내면 빈 값일 수 없다)", example = "퇴근러")
        @Size(min = 1, max = 50)
        String nickname,

        @Schema(description = "연령대. 10s, 20s, 30s, 40s, 50s 중 하나. 배너 매칭에 쓴다.", example = "20s")
        @Pattern(regexp = UserSignupRequest.AGE_GROUP_PATTERN)
        String ageGroup,

        @Schema(description = "성별. male, female 중 하나. 배너 매칭에 쓴다.", example = "female")
        @Pattern(regexp = UserSignupRequest.GENDER_PATTERN)
        String gender) {

    /**
     * 검증은 이 생성자가 끝난 뒤의 값에 실행되므로 앞뒤 공백을 먼저 지워 둔다.
     * 지우지 않으면 앞뒤 공백 때문에 50자를 넘는 것으로 판정되어, 실제로 저장할 값이 50자 이하인데도 400 이 된다.
     * 공백만 보낸 닉네임은 빈 문자열이 되어 @Size(min = 1) 에서 400 이 된다.
     */
    public UpdateUserRequest {
        nickname = nickname == null ? null : nickname.trim();
    }
}
