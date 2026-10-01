package com.techeer.backend.domain.banner.dto;

import com.techeer.backend.domain.campaign.entity.Campaign;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "노출할 배너")
public record BannerResponse(
        @Schema(description = "이번 노출의 알림 ID. 클릭 시 POST /api/v1/clicks 에 그대로 보낸다.",
                example = "3f2b8c1e-9a4d-4e7b-8c21-5d6f7a8b9c0d")
        String notificationId,
        @Schema(description = "캠페인 ID", example = "10") Long campaignId,
        @Schema(description = "배너 제목", example = "출근길 커피 1+1") String title,
        @Schema(description = "배너 본문") String body,
        @Schema(description = "배너 이미지 URL") String imageUrl,
        @Schema(description = "클릭 시 이동할 URL") String linkUrl) {

    public static BannerResponse of(String notificationId, Campaign campaign) {
        return new BannerResponse(
                notificationId,
                campaign.getCampaignId(),
                campaign.getTitle(),
                campaign.getBody(),
                campaign.getImageUrl(),
                campaign.getLinkUrl());
    }
}
