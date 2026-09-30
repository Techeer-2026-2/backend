package com.techeer.backend.domain.banner.controller;

import com.techeer.backend.domain.banner.dto.BannerResponse;
import com.techeer.backend.domain.banner.service.BannerService;
import com.techeer.backend.global.exception.ErrorResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Banner", description = "배너 노출 (연령대 매칭)")
public class BannerController {

    private final BannerService bannerService;

    @Operation(
            summary = "배너 노출 조회",
            description = "유저의 연령대와 타겟 연령대가 같은 진행 중 캠페인 중 1개를 배너로 돌려주고 노출을 기록한다. "
                    + "응답의 notificationId 는 클릭 기록(POST /api/v1/clicks)에 사용한다.")
    @ApiResponse(responseCode = "200", description = "매칭된 배너")
    @ApiResponse(responseCode = "204", description = "매칭되는 진행 중 캠페인 없음", content = @Content)
    @ApiResponse(responseCode = "400", description = "요청 값 오류",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "존재하지 않는 유저",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/banners/match")
    public ResponseEntity<BannerResponse> matchBanner(
            @Parameter(description = "배너를 볼 타겟 유저 ID", example = "100")
            @RequestParam Long targetUserId) {
        return bannerService.matchBanner(targetUserId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
