package com.techeer.backend.domain.advertiser.controller;

import com.techeer.backend.domain.advertiser.dto.AdvertiserResponse;
import com.techeer.backend.domain.advertiser.dto.SignupRequest;
import com.techeer.backend.domain.advertiser.dto.SignupResponse;
import com.techeer.backend.domain.advertiser.dto.UpdateAdvertiserRequest;
import com.techeer.backend.domain.advertiser.service.AdvertiserService;
import com.techeer.backend.domain.auth.resolver.AdvertiserId;
import com.techeer.backend.global.config.SwaggerConfig;
import com.techeer.backend.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    /**
     * 로그인한 광고주 본인의 정보를 조회한다. 광고주 ID 는 요청에서 받지 않고 access token 에서 꺼낸다.
     *
     * @param advertiserId access token 에서 꺼낸 광고주 ID
     * @return 광고주 정보
     */
    @Operation(
            summary = "내 정보 조회",
            description = "로그인한 광고주의 사업자명·이메일·요금제를 조회한다. 비밀번호(해시)는 응답에 포함되지 않는다.",
            security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "토큰이 없거나(AUTH_REQUIRED) 올바르지 않음(INVALID_TOKEN)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "탈퇴한 광고주",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/me")
    public AdvertiserResponse getMe(@AdvertiserId Long advertiserId) {
        return advertiserService.getMe(advertiserId);
    }

    /**
     * 로그인한 광고주 본인의 사업자명을 수정한다.
     *
     * @param advertiserId access token 에서 꺼낸 광고주 ID
     * @param request 새 사업자명
     * @return 수정된 광고주 정보
     */
    @Operation(
            summary = "내 정보 수정",
            description = "사업자명을 수정한다. 이메일·요금제는 수정할 수 없다(요청에 보내도 무시된다).",
            security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(responseCode = "400", description = "요청 값 오류",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "토큰이 없거나(AUTH_REQUIRED) 올바르지 않음(INVALID_TOKEN)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "탈퇴한 광고주",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/me")
    public AdvertiserResponse updateMe(
            @AdvertiserId Long advertiserId,
            @Valid @RequestBody UpdateAdvertiserRequest request) {
        return advertiserService.updateMe(advertiserId, request);
    }
}
