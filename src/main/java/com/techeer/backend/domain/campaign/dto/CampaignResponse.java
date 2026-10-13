package com.techeer.backend.domain.campaign.dto;

import com.techeer.backend.domain.campaign.entity.Campaign;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "캠페인")
public record CampaignResponse(
        @Schema(description = "캠페인 ID", example = "10") Long campaignId,
        @Schema(description = "배너 제목", example = "OOO 컴백 D-3") String title,
        @Schema(description = "배너 본문") String body,
        @Schema(description = "배너 이미지 URL") String imageUrl,
        @Schema(description = "클릭 시 이동할 URL. 없으면 배너 탭 자체가 클릭이다.") String linkUrl,
        @Schema(description = "타겟 연령대", example = "20s") String targetAgeGroup,
        @Schema(description = "노출 시작 시각") LocalDateTime startAt,
        @Schema(description = "노출 종료 시각") LocalDateTime endAt,
        @Schema(description = "진행 상태 (PENDING: 대기, ONGOING: 진행중, ENDED: 종료)") CampaignProgress status,
        @Schema(description = "등록 시각") LocalDateTime createdAt) {

    public static CampaignResponse of(Campaign campaign, LocalDateTime now) {
        return new CampaignResponse(
                campaign.getCampaignId(),
                campaign.getTitle(),
                campaign.getBody(),
                campaign.getImageUrl(),
                campaign.getLinkUrl(),
                campaign.getTargetAgeGroup(),
                campaign.getTimeStart(),
                campaign.getTimeEnd(),
                CampaignProgress.of(campaign.getTimeStart(), campaign.getTimeEnd(), now),
                campaign.getCreatedAt());
    }
}
