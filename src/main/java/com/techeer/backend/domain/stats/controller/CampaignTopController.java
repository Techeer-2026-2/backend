package com.techeer.backend.domain.stats.controller;

import com.techeer.backend.domain.auth.resolver.AdvertiserId;
import com.techeer.backend.domain.stats.dto.TopCampaignResponse;
import com.techeer.backend.domain.stats.service.TopCampaignService;
import com.techeer.backend.global.config.SwaggerConfig;
import com.techeer.backend.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CampaignTopController {

    private final TopCampaignService topCampaignService;

    public CampaignTopController(TopCampaignService topCampaignService) {
        this.topCampaignService = topCampaignService;
    }

    @Operation(
            summary = "최근 N분 클릭 상위 캠페인",
            description = "지금부터 windowMinutes 분 전까지 클릭이 많은 내 캠페인을 많은 순으로 돌려준다. "
                    + "클릭이 없는 캠페인은 빠진다. 클릭 수는 실시간 잠정값이다.",
            security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "클릭 많은 순 캠페인 목록 (없으면 빈 배열)")
    @ApiResponse(responseCode = "400", description = "windowMinutes(1~60) 또는 limit(1~20) 범위 밖, windowMinutes 누락",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "토큰 없음 또는 올바르지 않음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "없거나 탈퇴한 광고주",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/stats/top-campaigns")
    public ResponseEntity<List<TopCampaignResponse>> getTopCampaigns(
            @AdvertiserId Long advertiserId,
            @Parameter(description = "지금부터 몇 분 전까지 볼지 (1~60)", example = "10")
            @RequestParam int windowMinutes,
            @Parameter(description = "몇 개까지 (1~20, 기본 5)", example = "5")
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(topCampaignService.getTopCampaigns(advertiserId, windowMinutes, limit));
    }
}
