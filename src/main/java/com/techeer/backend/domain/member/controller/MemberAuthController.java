package com.techeer.backend.domain.member.controller;

import com.techeer.backend.domain.auth.dto.TokenResponse;
import com.techeer.backend.domain.member.dto.MemberLoginRequest;
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
 * 음악 앱 회원 가입·인증 API. 광고주 인증(/owners)과는 별개이며 토큰도 서로 바꿔 쓸 수 없다.
 */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
@Tag(name = "Member Auth", description = "음악 앱 회원 가입·로그인·토큰")
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

    @Operation(
            summary = "로그인",
            description = "이메일·비밀번호를 확인하고 JWT access token 과 refresh token 을 발급한다. "
                    + "이메일이 없는 경우와 비밀번호가 틀린 경우는 구분하지 않고 같은 401 로 응답한다.")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "400", description = "요청 값 오류",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody MemberLoginRequest request) {
        return memberAuthService.login(request);
    }
}
