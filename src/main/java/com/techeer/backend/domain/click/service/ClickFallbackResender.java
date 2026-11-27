package com.techeer.backend.domain.click.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.backend.domain.click.entity.ClickFallback;
import com.techeer.backend.domain.click.repository.ClickFallbackRepository;
import com.techeer.backend.domain.outbox.EventPublishException;
import com.techeer.backend.domain.outbox.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 보관된 클릭(PENDING)을 fallback_id 순서대로 ad-clicked 에 보내고 SENT 로 바꾼다.
 * Kafka 가 아직 안 되면 멈추고 PENDING 을 그대로 둔다. 순서를 지키려고 건너뛰지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClickFallbackResender {

    private final ClickFallbackRepository clickFallbackRepository;
    private final EventPublisher eventPublisher;
    private final ClickFallbackStatusService clickFallbackStatusService;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${click.fallback.retry-interval-ms:10000}")
    public void resend() {
        for (ClickFallback row : clickFallbackRepository
                .findTop500ByStatusOrderByFallbackIdAsc(ClickFallback.Status.PENDING)) {
            try {
                eventPublisher.publish(EventPublisher.AD_CLICKED, campaignKey(row.getPayload()), row.getPayload());
            } catch (EventPublishException e) {
                log.warn("보관 클릭 재전송 실패, 다음 주기에 재시도: fallback_id={}", row.getFallbackId(), e);
                return;
            }
            clickFallbackStatusService.markSent(row.getFallbackId());
        }
    }

    private String campaignKey(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            return node.get("campaign_id").asText();
        } catch (Exception e) {
            throw new EventPublishException("보관 본문에서 campaign_id 를 읽지 못함", e);
        }
    }
}
