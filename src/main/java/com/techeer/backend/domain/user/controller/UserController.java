package com.techeer.backend.domain.user.controller;

import com.techeer.backend.domain.user.dto.UpdateUserRequest;
import com.techeer.backend.domain.user.dto.UserResponse;
import com.techeer.backend.domain.user.service.UserService;
import com.techeer.backend.global.exception.ErrorResponse;
import com.techeer.backend.global.resolver.MemberId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 음악 앱 회원 본인의 정보 API. 광고주 내 정보(/owners/me)와는 별개다.
 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "User", description = "음악 앱 회원 내 정보")
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "내 정보 조회",
            description = "로그인한 회원의 이메일·닉네임을 돌려준다. Authorization: Bearer 회원 access token 으로 식별한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "토큰이 없거나 올바르지 않음(광고주 토큰 포함)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "탈퇴한 회원",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public UserResponse getMe(@MemberId Long userId) {
        return userService.getMe(userId);
    }

    @Operation(
            summary = "내 정보 수정",
            description = "닉네임을 수정한다. 이메일·비밀번호는 이 API 로 바꿀 수 없다.")
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(responseCode = "400", description = "요청 값 오류",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "토큰이 없거나 올바르지 않음(광고주 토큰 포함)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "탈퇴한 회원",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping
    public UserResponse updateMe(@MemberId Long userId, @Valid @RequestBody UpdateUserRequest request) {
        return userService.updateMe(userId, request);
    }
}
