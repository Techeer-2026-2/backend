package com.techeer.backend.domain.stats.controller;

import com.techeer.backend.domain.auth.resolver.AdvertiserId;
import com.techeer.backend.domain.stats.service.CampaignReportService;
import com.techeer.backend.global.config.SwaggerConfig;
import com.techeer.backend.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CampaignReportController {

    private final CampaignReportService campaignReportService;

    public CampaignReportController(CampaignReportService campaignReportService) {
        this.campaignReportService = campaignReportService;
    }

    @Operation(
            summary = "캠페인 성과 리포트 CSV 다운로드",
            description = "날짜별 노출 수·클릭 수·CTR 을 CSV 파일로 돌려준다. 기간을 생략하면 최근 7일. "
                    + "엑셀에서 한글이 깨지지 않도록 UTF-8 BOM 을 붙인다.",
            security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "CSV 파일 (attachment)")
    @ApiResponse(responseCode = "400", description = "기간 역전, 구간 수 초과(INVALID_INPUT)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "토큰 없음 또는 올바르지 않음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "내 캠페인이 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "존재하지 않거나 삭제된 캠페인",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/campaigns/{campaignId}/report/csv")
    public ResponseEntity<byte[]> downloadCsv(
            @PathVariable Long campaignId,
            @AdvertiserId Long advertiserId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        byte[] csv = campaignReportService.buildDailyCsv(advertiserId, campaignId, from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("campaign-" + campaignId + "-report.csv", java.nio.charset.StandardCharsets.UTF_8)
                        .build().toString())
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(csv);
    }
}
