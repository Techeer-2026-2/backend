package com.techeer.backend.domain.campaign.dto;

import com.techeer.backend.domain.campaign.entity.Campaign;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Schema(description = "캠페인")
public record CampaignResponse(
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
        @Schema(description = "등록 시각") LocalDateTime createdAt,
        @Schema(description = "노출수 (목록 표용)", example = "1200") long impressionCount,
        @Schema(description = "클릭수 (목록 표용)", example = "300") long clickCount) {

    public static CampaignResponse of(Campaign campaign, LocalDateTime now) {
        return of(campaign, now, 0, 0);
    }

    public static CampaignResponse of(Campaign campaign, LocalDateTime now, long impressionCount, long clickCount) {
        return new CampaignResponse(
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
                campaign.getCreatedAt(),
                impressionCount,
                clickCount);
    }

    private static List<String> sorted(Set<String> values) {
        return values.stream().sorted().toList();
    }
}
