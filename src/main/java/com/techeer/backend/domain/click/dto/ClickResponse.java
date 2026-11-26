package com.techeer.backend.domain.click.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "전송된 클릭 (DB 행이 아니라 Kafka 전송 결과)")
public record ClickResponse(
        @Schema(description = "알림 ID", example = "3f2b8c1e-9a4d-4e7b-8c21-5d6f7a8b9c0d") String notificationId,
        @Schema(description = "캠페인 ID", example = "10") Long campaignId,
        @Schema(description = "클릭 요청 시각") LocalDateTime clickedAt) {

    public static ClickResponse of(String notificationId, Long campaignId, LocalDateTime clickedAt) {
        return new ClickResponse(notificationId, campaignId, clickedAt);
    }
}
