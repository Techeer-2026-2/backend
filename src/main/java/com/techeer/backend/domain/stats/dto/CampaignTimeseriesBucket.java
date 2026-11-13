package com.techeer.backend.domain.stats.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "추이 그래프의 한 구간 (시간 또는 일)")
public record CampaignTimeseriesBucket(
        @Schema(description = "구간 시작 시각", example = "2026-11-12T00:00:00") LocalDateTime bucketStart,
        @Schema(description = "노출 수", example = "120") long impressionCount,
        @Schema(description = "클릭 수", example = "8") long clickCount,
        @Schema(description = "클릭률(%) = 클릭 ÷ 노출 × 100. 노출이 0이면 0", example = "6.67") double ctr,
        @Schema(description = "소진액(원) = 이 구간 노출의 실제 차감액 합", example = "3720") long spentAmount,
        @Schema(description = "클릭당 비용(원) = 소진액 ÷ 클릭. 클릭이 0이면 0", example = "465.0") double cpc,
        @Schema(description = "노출 1000회당 비용(원) = 소진액 ÷ 노출 × 1000. 노출이 0이면 0", example = "31000.0") double cpm) {

    public static CampaignTimeseriesBucket of(LocalDateTime bucketStart, long impressionCount, long clickCount,
            long spentAmount) {
        double ctr = impressionCount > 0 ? round2((double) clickCount / impressionCount * 100) : 0.0;
        double cpc = clickCount > 0 ? round2((double) spentAmount / clickCount) : 0.0;
        double cpm = impressionCount > 0 ? round2((double) spentAmount / impressionCount * 1000) : 0.0;
        return new CampaignTimeseriesBucket(bucketStart, impressionCount, clickCount, ctr, spentAmount, cpc, cpm);
    }

    /** 소수 둘째 자리까지 반올림 */
    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
