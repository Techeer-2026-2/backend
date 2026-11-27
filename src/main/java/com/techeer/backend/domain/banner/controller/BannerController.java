package com.techeer.backend.domain.banner.controller;

import com.techeer.backend.domain.banner.dto.BannerResponse;
import com.techeer.backend.domain.banner.service.BannerService;
import com.techeer.backend.global.exception.ErrorResponse;
import com.techeer.backend.global.resolver.MemberId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Banner", description = "배너 노출 (연령대 매칭)")
public class BannerController {

    private final BannerService bannerService;

    @Operation(
            summary = "배너 노출 조회",
            description = "로그인한 회원의 연령대·성별에 맞는 진행 중 캠페인 중 1개를 배너로 돌려주고 노출을 기록한다. "
                    + "회원은 Authorization: Bearer 회원 access token(또는 X-Member-Id 헤더)으로 식별한다. "
                    + "연령대를 입력하지 않은 회원(PATCH /api/v1/me 로 입력)은 204 를 받는다. "
                    + "응답의 notificationId 는 클릭 기록(POST /api/v1/clicks)에 사용한다.")
    @ApiResponse(responseCode = "200", description = "매칭된 배너")
    @ApiResponse(responseCode = "204", description = "매칭되는 진행 중 캠페인이 없거나 회원 연령대가 비어 있음",
            content = @Content)
    @ApiResponse(responseCode = "400", description = "회원 식별 헤더가 없거나 올바르지 않음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "토큰이 올바르지 않음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "없거나 탈퇴한 회원",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/banners/match")
    public ResponseEntity<BannerResponse> matchBanner(@Parameter(hidden = true) @MemberId Long memberId) {
        return bannerService.matchBanner(memberId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
