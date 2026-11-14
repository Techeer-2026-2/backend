package com.techeer.backend.domain.stats.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "연령대별 노출·클릭·CTR")
public record CampaignAgeStats(
        @Schema(description = "연령대 (10s, 20s, 30s, 40s, 50s). 모르면 unknown", example = "20s") String ageGroup,
        @Schema(description = "노출 수", example = "120") long impressionCount,
        @Schema(description = "클릭 수", example = "8") long clickCount,
        @Schema(description = "클릭률(%) = 클릭 ÷ 노출 × 100. 노출이 0이면 0", example = "6.67") double ctr) {

    public static CampaignAgeStats of(String ageGroup, long impressionCount, long clickCount) {
        double ctr = impressionCount > 0 ? Math.round((double) clickCount / impressionCount * 10000) / 100.0 : 0.0;
        return new CampaignAgeStats(ageGroup, impressionCount, clickCount, ctr);
    }
}
