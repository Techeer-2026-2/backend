package com.techeer.backend.domain.campaign.dto;

import com.techeer.backend.domain.campaign.entity.Campaign;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "캠페인 상세 (통계 포함)")
public record CampaignDetailResponse(
        @Schema(description = "캠페인 ID", example = "10") Long campaignId,
        @Schema(description = "배너 제목", example = "OOO 컴백 D-3") String title,
        @Schema(description = "배너 본문") String body,
        @Schema(description = "배너 이미지 URL") String imageUrl,
        @Schema(description = "클릭 시 이동할 URL. 없으면 배너 탭 자체가 클릭이다.") String linkUrl,
        @Schema(description = "타겟 연령대", example = "20s") String targetAgeGroup,
        @Schema(description = "노출 시작 시각") LocalDateTime startAt,
        @Schema(description = "노출 종료 시각") LocalDateTime endAt,
        @Schema(description = "진행 상태 (PENDING: 대기, ONGOING: 진행중, ENDED: 종료)") CampaignProgress status,
        @Schema(description = "노출수", example = "1200") long impressionCount,
        @Schema(description = "클릭수", example = "300") long clickCount,
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
                campaign.getTargetAgeGroup(),
                campaign.getTimeStart(),
                campaign.getTimeEnd(),
                CampaignProgress.of(campaign.getTimeStart(), campaign.getTimeEnd(), now),
                impressionCount,
                clickCount,
                campaign.getCreatedAt(),
                campaign.getUpdatedAt());
    }
}
