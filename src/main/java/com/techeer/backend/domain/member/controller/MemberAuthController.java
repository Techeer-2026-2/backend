package com.techeer.backend.domain.member.controller;

import com.techeer.backend.domain.member.dto.MemberSignupRequest;
import com.techeer.backend.domain.member.dto.MemberSignupResponse;
import com.techeer.backend.domain.member.service.MemberAuthService;
import com.techeer.backend.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 음악 앱 회원 가입 API. 광고주 계정(/owners)과는 별개이다.
 */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
@Tag(name = "Member Auth", description = "음악 앱 회원 가입")
public class MemberAuthController {

    private final MemberAuthService memberAuthService;

    @Operation(
            summary = "회원가입",
            description = "이메일·비밀번호·닉네임으로 음악 앱 회원을 만든다. 비밀번호는 bcrypt 해시로 저장된다.")
    @ApiResponse(responseCode = "201", description = "가입 완료")
    @ApiResponse(responseCode = "400", description = "요청 값 오류",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "이미 가입된 이메일",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/signup")
    public ResponseEntity<MemberSignupResponse> signup(@Valid @RequestBody MemberSignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memberAuthService.signup(request));
    }
}
