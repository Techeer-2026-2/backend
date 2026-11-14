package com.techeer.backend.domain.stats.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "추이 그래프의 한 구간 (시간 또는 일)")
public record CampaignTimeseriesBucket(
        @Schema(description = "구간 시작 시각", example = "2026-11-12T00:00:00") LocalDateTime bucketStart,
        @Schema(description = "노출 수", example = "120") long impressionCount,
        @Schema(description = "클릭 수", example = "8") long clickCount,
        @Schema(description = "클릭률(%) = 클릭 ÷ 노출 × 100. 노출이 0이면 0", example = "6.67") double ctr) {

    public static CampaignTimeseriesBucket of(LocalDateTime bucketStart, long impressionCount, long clickCount) {
        double ctr = impressionCount > 0 ? Math.round((double) clickCount / impressionCount * 10000) / 100.0 : 0.0;
        return new CampaignTimeseriesBucket(bucketStart, impressionCount, clickCount, ctr);
    }
}
