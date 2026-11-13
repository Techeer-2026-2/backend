package com.techeer.backend.domain.campaign.dto;

import com.techeer.backend.domain.campaign.entity.Campaign;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Schema(description = "캠페인 상세 (통계 포함)")
public record CampaignDetailResponse(
        @Schema(description = "캠페인 ID", example = "10") Long campaignId,
        @Schema(description = "배너 제목", example = "OOO 컴백 D-3") String title,
        @Schema(description = "배너 본문") String body,
        @Schema(description = "배너 이미지 URL") String imageUrl,
        @Schema(description = "클릭 시 이동할 URL. 없으면 배너 탭 자체가 클릭이다.") String linkUrl,
        @Schema(description = "타겟 연령대 목록", example = "[\"20s\"]") List<String> targetAgeGroups,
        @Schema(description = "타겟 성별 목록", example = "[\"all\"]") List<String> targetGenders,
        @Schema(description = "노출 시작 시각") LocalDateTime startAt,
        @Schema(description = "노출 종료 시각") LocalDateTime endAt,
        @Schema(description = "진행 상태 (PENDING: 대기, ONGOING: 진행중, ENDED: 종료)") CampaignProgress status,
        @Schema(description = "노출수", example = "1200") long impressionCount,
        @Schema(description = "클릭수", example = "300") long clickCount,
        @Schema(description = "총 예산(원)", example = "100000") long totalBudget,
        @Schema(description = "입찰가(원). 노출 1회당 최대 금액", example = "50") long bidPrice,
        @Schema(description = "누적 소진액(원)", example = "32000") long spentTotal,
        @Schema(description = "남은 예산(원) = 총 예산 - 누적 소진액", example = "68000") long remainingBudget,
        @Schema(description = "목표 클릭수", example = "300") long targetClicks,
        @Schema(description = "예산 소진율(%) = 누적 소진액 ÷ 총 예산 × 100. 총 예산이 0이면 0",
                example = "32.0") double budgetUsageRate,
        @Schema(description = "목표 클릭 달성률(%) = 클릭수 ÷ 목표 클릭수 × 100. 목표가 0이면 0",
                example = "100.0") double targetClickRate,
        @Schema(description = "등록 시각") LocalDateTime createdAt,
        @Schema(description = "마지막 수정 시각") LocalDateTime updatedAt) {

    public static CampaignDetailResponse of(Campaign campaign, long impressionCount, long clickCount,
            LocalDateTime now) {
        return new CampaignDetailResponse(
                campaign.getCampaignId(),
                campaign.getTitle(),
                campaign.getBody(),
                campaign.getImageUrl(),
                campaign.getLinkUrl(),
                sorted(campaign.getAgeGroups()),
                sorted(campaign.getGenders()),
                campaign.getTimeStart(),
                campaign.getTimeEnd(),
                CampaignProgress.of(campaign.getTimeStart(), campaign.getTimeEnd(), now),
                impressionCount,
                clickCount,
                campaign.getTotalBudget(),
                campaign.getBidPrice(),
                campaign.getSpentTotal(),
                campaign.getRemainingBudget(),
                campaign.getTargetClicks(),
                percent(campaign.getSpentTotal(), campaign.getTotalBudget()),
                percent(clickCount, campaign.getTargetClicks()),
                campaign.getCreatedAt(),
                campaign.getUpdatedAt());
    }

    /** part ÷ whole × 100 을 소수 둘째 자리로 반올림한다. whole 이 0 이면 0. */
    public static double percent(long part, long whole) {
        if (whole <= 0) {
            return 0.0;
        }
        return Math.round((double) part / whole * 10000) / 100.0;
    }

    private static List<String> sorted(Set<String> values) {
        return values.stream().sorted().toList();
    }
}
