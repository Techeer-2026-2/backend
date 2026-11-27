package com.techeer.backend.domain.click.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Kafka ad-clicked 본문 (지시서 §3-2). 노출 본문과 같은 키에 target_user_id 를 더한다.
 * target_user_id 는 DB 사본 click_events 를 채울 때 쓴다.
 */
public record ClickEventPayload(
        @JsonProperty("notification_id") String notificationId,
        @JsonProperty("campaign_id") Long campaignId,
        @JsonProperty("age_group") String ageGroup,
        @JsonProperty("gender") String gender,
        @JsonProperty("event_at") String eventAt,
        @JsonProperty("target_user_id") Long targetUserId) {
}
