package com.techeer.backend.domain.account.controller;

import com.techeer.backend.domain.account.dto.AuthDtos.Data;
import com.techeer.backend.domain.account.dto.AuthDtos.LoginRequest;
import com.techeer.backend.domain.account.dto.AuthDtos.LoginResponse;
import com.techeer.backend.domain.account.dto.AuthDtos.SignUpRequest;
import com.techeer.backend.domain.account.dto.AuthDtos.UserResponse;
import com.techeer.backend.domain.account.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "음악 앱 일반 계정")
public class AuthController {
    private final AuthService service;

    @PostMapping("/signup")
    @Operation(summary = "이메일로 일반 계정 회원가입")
    public ResponseEntity<Data<UserResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new Data<>(service.signUp(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "이메일·비밀번호 로그인 및 액세스 토큰 발급")
    public Data<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return new Data<>(service.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "로그인한 일반 계정 조회", security = @SecurityRequirement(name = "bearerAuth"))
    public Data<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return new Data<>(service.currentUser(jwt.getSubject()));
    }
}
