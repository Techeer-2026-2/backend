package com.techeer.backend.domain.outbox.service;

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
            try {
                eventPublisher.publish(EventPublisher.AD_IMPRESSED, campaignKey(row.getPayload()), row.getPayload());
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

    /** 키는 campaign_id 문자열. 같은 캠페인은 같은 파티션으로 가서 순서가 유지된다. */
    private String campaignKey(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            return node.get("campaign_id").asText();
        } catch (Exception e) {
            throw new EventPublishException("아웃박스 본문에서 campaign_id 를 읽지 못함", e);
        }
    }
}
