package com.techeer.backend.domain.outbox.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Kafka 로 보내는 노출·클릭 이벤트 본문 (지시서 §3-2). 키 이름은 snake_case 로 고정한다.
 * eventAt 은 서버가 받은 한국 시간, 초 단위 (yyyy-MM-dd HH:mm:ss).
 */
public record AdEventPayload(
        @JsonProperty("notification_id") String notificationId,
        @JsonProperty("campaign_id") Long campaignId,
        @JsonProperty("age_group") String ageGroup,
        @JsonProperty("gender") String gender,
        @JsonProperty("event_at") String eventAt) {
}
