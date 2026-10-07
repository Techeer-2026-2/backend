package com.techeer.backend.domain.stats.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "캠페인 실시간 통계 (SSE 이벤트 'stats' 의 data)")
public record CampaignStatsEvent(
        @Schema(description = "캠페인 ID", example = "10") Long campaignId,
        @Schema(description = "노출 수", example = "120") long sentCount,
        @Schema(description = "클릭 수", example = "30") long clickCount,
        @Schema(description = "클릭률(클릭 수 / 노출 수). 노출이 없으면 0", example = "0.25") double clickRate) {

    /**
     * 노출·클릭 수로 이벤트를 만든다. 집계 행이 아직 없는 캠페인은 0, 0 을 넘기면 된다.
     */
    public static CampaignStatsEvent of(Long campaignId, long sentCount, long clickCount) {
        double clickRate = sentCount == 0 ? 0.0 : (double) clickCount / sentCount;
        return new CampaignStatsEvent(campaignId, sentCount, clickCount, clickRate);
    }
}
