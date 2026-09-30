package com.techeer.backend.domain.click.dto;

import com.techeer.backend.domain.click.entity.ClickEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "기록된 클릭")
public record ClickResponse(
        @Schema(description = "클릭 ID", example = "1") Long clickId,
        @Schema(description = "알림 ID", example = "3f2b8c1e-9a4d-4e7b-8c21-5d6f7a8b9c0d") String notificationId,
        @Schema(description = "캠페인 ID", example = "10") Long campaignId,
        @Schema(description = "클릭 시각 (최초 클릭 기준)") LocalDateTime clickedAt) {

    public static ClickResponse from(ClickEvent clickEvent) {
        return new ClickResponse(
                clickEvent.getClickId(),
                clickEvent.getNotificationId(),
                clickEvent.getCampaignId(),
                clickEvent.getClickedAt());
    }
}
