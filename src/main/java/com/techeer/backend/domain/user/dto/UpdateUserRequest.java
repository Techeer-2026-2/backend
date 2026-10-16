package com.techeer.backend.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 내 정보 수정 요청. 지금은 닉네임만 바꿀 수 있다. 이메일·비밀번호는 이 요청으로 바꿀 수 없도록 필드 자체를 두지 않았다.
 */
@Schema(description = "음악 앱 회원 내 정보 수정 요청")
public record UpdateUserRequest(
        @Schema(description = "새 닉네임", example = "퇴근러")
        @NotBlank
        @Size(max = 50)
        String nickname) {

    /**
     * 검증은 이 생성자가 끝난 뒤의 값에 실행되므로 앞뒤 공백을 먼저 지워 둔다.
     * 지우지 않으면 앞뒤 공백 때문에 50자를 넘는 것으로 판정되어, 실제로 저장할 값이 50자 이하인데도 400 이 된다.
     */
    public UpdateUserRequest {
        nickname = nickname == null ? null : nickname.trim();
    }
}
