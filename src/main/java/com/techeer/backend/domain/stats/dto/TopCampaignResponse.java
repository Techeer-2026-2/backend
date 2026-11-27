package com.techeer.backend.domain.stats.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "최근 N분 클릭 상위 캠페인 한 줄")
public record TopCampaignResponse(
        @Schema(description = "캠페인 ID", example = "10") Long campaignId,
        @Schema(description = "캠페인 제목", example = "출근길 커피 1+1") String title,
        @Schema(description = "기간 안의 클릭 수 (실시간 잠정값)", example = "42") long clickCount) {
}
