package com.techeer.backend.domain.stats.controller;

import com.techeer.backend.domain.auth.resolver.AdvertiserId;
import com.techeer.backend.domain.stats.dto.CampaignSummaryResponse;
import com.techeer.backend.domain.stats.service.CampaignSummaryService;
import com.techeer.backend.global.config.SwaggerConfig;
import com.techeer.backend.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CampaignSummaryController {

    private final CampaignSummaryService campaignSummaryService;

    public CampaignSummaryController(CampaignSummaryService campaignSummaryService) {
        this.campaignSummaryService = campaignSummaryService;
    }

    @Operation(
            summary = "캠페인 성과 AI 요약",
            description = "누적 노출·클릭·클릭률 숫자를 Claude 에 보내 한국어 요약 문장을 받는다. "
                    + "서버에 ANTHROPIC_API_KEY 가 없으면 503 이다.",
            security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "요약 문장")
    @ApiResponse(responseCode = "401", description = "토큰 없음 또는 올바르지 않음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "내 캠페인이 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "존재하지 않거나 삭제된 캠페인",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "502", description = "Claude API 호출 실패 (AI_CALL_FAILED)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "AI 키가 설정되지 않음 (AI_NOT_CONFIGURED)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/campaigns/{campaignId}/summary")
    public ResponseEntity<CampaignSummaryResponse> summarize(
            @PathVariable Long campaignId,
            @AdvertiserId Long advertiserId) {
        return ResponseEntity.ok(campaignSummaryService.summarize(advertiserId, campaignId));
    }
}
