package com.techeer.backend.domain.stats.controller;

import com.techeer.backend.domain.auth.resolver.AdvertiserId;
import com.techeer.backend.domain.stats.dto.CampaignAgeStats;
import com.techeer.backend.domain.stats.service.CampaignAgeStatsService;
import com.techeer.backend.global.config.SwaggerConfig;
import com.techeer.backend.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CampaignAgeStatsController {

    private final CampaignAgeStatsService campaignAgeStatsService;

    public CampaignAgeStatsController(CampaignAgeStatsService campaignAgeStatsService) {
        this.campaignAgeStatsService = campaignAgeStatsService;
    }

    @Operation(
            summary = "캠페인 연령대별 노출·클릭·CTR",
            description = "기간 안의 노출·클릭을 연령대(10s~50s)별로 나눠 CTR 을 돌려준다. "
                    + "연령을 모르는 노출은 unknown 으로 따로 나온다. 기간을 생략하면 최근 7일.",
            security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "연령대별 통계")
    @ApiResponse(responseCode = "400", description = "기간 역전(INVALID_INPUT)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "토큰 없음 또는 올바르지 않음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "내 캠페인이 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "존재하지 않거나 삭제된 캠페인",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/campaigns/{campaignId}/stats/by-age")
    public ResponseEntity<List<CampaignAgeStats>> getByAge(
            @PathVariable Long campaignId,
            @AdvertiserId Long advertiserId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(campaignAgeStatsService.getByAge(advertiserId, campaignId, from, to));
    }
}
