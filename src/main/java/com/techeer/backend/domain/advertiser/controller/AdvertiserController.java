package com.techeer.backend.domain.advertiser.controller;

import com.techeer.backend.domain.advertiser.dto.SignupRequest;
import com.techeer.backend.domain.advertiser.dto.SignupResponse;
import com.techeer.backend.domain.advertiser.service.AdvertiserService;
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
 * 광고주 계정 API. 주소는 Notion 명세에 맞춰 /owners 를 유지한다.
 */
@RestController
@RequestMapping("/api/v1/owners")
@RequiredArgsConstructor
@Tag(name = "Owner", description = "광고주 계정")
public class AdvertiserController {

    private final AdvertiserService advertiserService;

    /**
     * 광고주 회원가입 요청을 받아 가입 결과를 201 로 반환한다.
     *
     * @param request 이메일, 비밀번호, 사업자명
     * @return 가입된 광고주 정보
     */
    @Operation(
            summary = "광고주 회원가입",
            description = "이메일·비밀번호·사업자명으로 광고주를 만든다. 비밀번호는 bcrypt 해시로 저장되고 요금제는 FREE 로 시작한다.")
    @ApiResponse(responseCode = "201", description = "가입 완료")
    @ApiResponse(responseCode = "400", description = "요청 값 오류",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "이미 가입된 이메일",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(advertiserService.signup(request));
    }
}
