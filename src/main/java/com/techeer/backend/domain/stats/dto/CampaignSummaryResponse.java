package com.techeer.backend.domain.stats.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "캠페인 성과 AI 요약")
public record CampaignSummaryResponse(
        @Schema(description = "캠페인 ID", example = "10") Long campaignId,
        @Schema(description = "요약 문장 (한국어)", example = "노출 1,200회 중 클릭 300회로 클릭률은 25.00%입니다.")
        String summary) {
}
