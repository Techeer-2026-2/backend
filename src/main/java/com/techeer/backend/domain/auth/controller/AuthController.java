package com.techeer.backend.domain.auth.controller;

import com.techeer.backend.domain.auth.dto.LoginRequest;
import com.techeer.backend.domain.auth.dto.TokenResponse;
import com.techeer.backend.domain.auth.service.AuthService;
import com.techeer.backend.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 광고주 인증 API. 주소는 Notion 명세에 맞춰 /owners 를 유지한다.
 */
@RestController
@RequestMapping("/api/v1/owners")
@RequiredArgsConstructor
@Tag(name = "Owner Auth", description = "광고주 로그인·토큰")
public class AuthController {

    private final AuthService authService;

    /**
     * 이메일·비밀번호로 로그인하고 토큰을 발급한다.
     *
     * @param request 이메일과 비밀번호
     * @return access/refresh token
     */
    @Operation(
            summary = "광고주 로그인",
            description = "이메일·비밀번호를 확인하고 JWT access token 과 refresh token 을 발급한다. "
                    + "이메일이 없는 경우와 비밀번호가 틀린 경우는 구분하지 않고 같은 401 로 응답한다.")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "400", description = "요청 값 오류",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
