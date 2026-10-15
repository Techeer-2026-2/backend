package com.techeer.backend.domain.user.dto;

import com.techeer.backend.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "음악 앱 회원가입 응답 (비밀번호·해시는 포함하지 않는다)")
public record UserSignupResponse(
        @Schema(description = "회원 ID", example = "1") Long userId,
        @Schema(description = "이메일", example = "listener@techeer.com") String email,
        @Schema(description = "닉네임", example = "출근러") String nickname,
        @Schema(description = "가입 시각") LocalDateTime createdAt) {

    /**
     * 저장된 회원 엔티티에서 응답에 필요한 값만 꺼내 만든다. 엔티티를 그대로 반환하지 않기 위한 변환이다.
     */
    public static UserSignupResponse from(User user) {
        return new UserSignupResponse(
                user.getUserId(), user.getEmail(), user.getNickname(), user.getCreatedAt());
    }
}
