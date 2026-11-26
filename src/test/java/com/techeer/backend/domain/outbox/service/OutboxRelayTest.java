package com.techeer.backend.domain.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.backend.domain.outbox.EventPublishException;
import com.techeer.backend.domain.outbox.EventPublisher;
import com.techeer.backend.domain.outbox.entity.OutboxEvent;
import com.techeer.backend.domain.outbox.repository.OutboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

/** 릴레이 순서와 실패 시 멈춤 규칙을 확인한다 (Kafka 와 DB 는 목으로 대체). */
class OutboxRelayTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final EventPublisher publisher = mock(EventPublisher.class);
    private final OutboxStatusService status = mock(OutboxStatusService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-26T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final OutboxRelay relay = new OutboxRelay(repository, publisher, status, new ObjectMapper(), clock);

    private static OutboxEvent row(long id, long campaignId) {
        OutboxEvent event = OutboxEvent.create("AD_IMPRESSED", "n-" + id,
                "{\"notification_id\":\"n-" + id + "\",\"campaign_id\":" + campaignId + "}",
                java.time.LocalDateTime.now());
        ReflectionTestUtils.setField(event, "outboxId", id);
        return event;
    }

    @Test
    void 대기_행을_순서대로_발행하고_각각_발행_완료로_표시한다() {
        when(repository.findTop500ByStatusOrderByOutboxIdAsc(OutboxEvent.Status.PENDING))
                .thenReturn(List.of(row(1, 12), row(2, 12)));

        relay.relay();

        InOrder order = inOrder(publisher, status);
        order.verify(publisher).publish(eq("ad-impressed"), eq("12"), anyString());
        order.verify(status).markPublished(1L);
        order.verify(publisher).publish(eq("ad-impressed"), eq("12"), anyString());
        order.verify(status).markPublished(2L);
    }

    @Test
    void 발행이_실패하면_그_행에서_멈추고_뒤의_행은_건드리지_않는다() {
        when(repository.findTop500ByStatusOrderByOutboxIdAsc(OutboxEvent.Status.PENDING))
                .thenReturn(List.of(row(1, 12), row(2, 12), row(3, 12)));
        doThrow(new EventPublishException("끊김", null))
                .when(publisher).publish(eq("ad-impressed"), eq("12"), any());

        relay.relay();

        verify(status, never()).markPublished(any());
        verify(publisher, org.mockito.Mockito.times(1)).publish(any(), any(), any());
    }

    @Test
    void 정리_작업은_하루_지난_발행_완료_행을_지운다() {
        relay.cleanupPublished();

        verify(status).deletePublishedBefore(java.time.LocalDateTime.of(2026, 11, 25, 12, 0));
    }
}
