package com.techeer.backend.domain.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.backend.domain.outbox.EventPublishException;
import com.techeer.backend.domain.outbox.EventPublisher;
import com.techeer.backend.domain.outbox.entity.OutboxEvent;
import com.techeer.backend.domain.outbox.repository.OutboxEventRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 아웃박스 릴레이. PENDING 행을 outbox_id 순서대로 Kafka ad-impressed 에 보내고 PUBLISHED 로 바꾼다.
 * 발행이 실패하면 거기서 멈추고 남은 행은 다음 주기에 다시 보낸다 (순서를 지키기 위해 건너뛰지 않는다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final EventPublisher eventPublisher;
    private final OutboxStatusService outboxStatusService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${relay.outbox.interval-ms:1000}")
    public void relay() {
        for (OutboxEvent row : outboxEventRepository.findTop500ByStatusOrderByOutboxIdAsc(OutboxEvent.Status.PENDING)) {
            String key = campaignKey(row.getPayload());
            if (key == null) {
                log.error("아웃박스 본문을 읽지 못해 이 행만 건너뜀: id={}", row.getOutboxId());
                continue;
            }
            try {
                eventPublisher.publish(EventPublisher.AD_IMPRESSED, key, row.getPayload());
            } catch (EventPublishException e) {
                log.warn("아웃박스 발행 실패, 다음 주기에 재시도: outbox_id={}", row.getOutboxId(), e);
                return;
            }
            outboxStatusService.markPublished(row.getOutboxId());
        }
    }

    /** 발행 완료 후 하루가 지난 행을 지운다. 매일 새벽 4시 30분 (서울). */
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void cleanupPublished() {
        int deleted = outboxStatusService.deletePublishedBefore(LocalDateTime.now(clock).minusDays(1));
        if (deleted > 0) {
            log.info("발행 완료 아웃박스 행 정리: {}건", deleted);
        }
    }

    /** 키는 campaign_id 문자열. 읽지 못하면 null 을 돌려주고, 호출한 쪽이 그 행만 건너뛴다. */
    private String campaignKey(String payload) {
        try {
            JsonNode campaignId = objectMapper.readTree(payload).get("campaign_id");
            return campaignId == null || campaignId.isNull() ? null : campaignId.asText();
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
