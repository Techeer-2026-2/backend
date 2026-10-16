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
}
