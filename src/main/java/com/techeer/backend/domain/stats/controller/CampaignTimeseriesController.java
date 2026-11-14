package com.techeer.backend.domain.stats.controller;

import com.techeer.backend.domain.auth.resolver.AdvertiserId;
import com.techeer.backend.domain.stats.dto.CampaignTimeseriesBucket;
import com.techeer.backend.domain.stats.service.CampaignTimeseriesService;
import com.techeer.backend.global.config.SwaggerConfig;
import com.techeer.backend.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
public class CampaignTimeseriesController {

    private final CampaignTimeseriesService campaignTimeseriesService;

    public CampaignTimeseriesController(CampaignTimeseriesService campaignTimeseriesService) {
        this.campaignTimeseriesService = campaignTimeseriesService;
    }

    @Operation(
            summary = "캠페인 노출·클릭 추이 조회",
            description = "기간을 시간(hour) 또는 일(day) 단위로 나눈 노출·클릭·CTR 목록을 돌려준다. "
                    + "기록이 없는 구간은 0이다. 기간을 생략하면 최근 7일, 단위를 생략하면 day.",
            security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "구간별 추이 (시작 시각 오름차순)")
    @ApiResponse(responseCode = "400", description = "기간 역전, 잘못된 단위, 구간 수 초과(INVALID_INPUT)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "토큰 없음 또는 올바르지 않음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "내 캠페인이 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "존재하지 않거나 삭제된 캠페인",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/campaigns/{campaignId}/stats/timeseries")
    public ResponseEntity<List<CampaignTimeseriesBucket>> getTimeseries(
            @PathVariable Long campaignId,
            @AdvertiserId Long advertiserId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String granularity) {
        return ResponseEntity.ok(campaignTimeseriesService.getTimeseries(advertiserId, campaignId, from, to, granularity));
    }
}
