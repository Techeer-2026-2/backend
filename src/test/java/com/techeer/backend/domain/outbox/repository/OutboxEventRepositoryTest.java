package com.techeer.backend.domain.outbox.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.outbox.entity.OutboxEvent;
import com.techeer.backend.global.config.ClockConfig;
import com.techeer.backend.global.config.JpaConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** 아웃박스 조회 규칙을 실제 PostgreSQL(Testcontainers)에서 검증한다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JpaConfig.class, ClockConfig.class})
class OutboxEventRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 26, 12, 0);

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void 저장하면_PENDING_으로_시작하고_발행_완료하면_PUBLISHED_가_된다() {
        OutboxEvent saved = outboxEventRepository.saveAndFlush(
                OutboxEvent.create("AD_IMPRESSED", "n-1", "{\"n\":1}", NOW));
        assertThat(saved.getOutboxId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);

        saved.markPublished(NOW.plusSeconds(1));
        outboxEventRepository.flush();

        OutboxEvent reloaded = outboxEventRepository.findById(saved.getOutboxId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxEvent.Status.PUBLISHED);
        assertThat(reloaded.getPublishedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void 미발행_행만_outbox_id_순서로_가져온다() {
        OutboxEvent first = outboxEventRepository.saveAndFlush(
                OutboxEvent.create("AD_IMPRESSED", "n-a", "{}", NOW));
        OutboxEvent second = outboxEventRepository.saveAndFlush(
                OutboxEvent.create("AD_IMPRESSED", "n-b", "{}", NOW));
        OutboxEvent third = outboxEventRepository.saveAndFlush(
                OutboxEvent.create("AD_IMPRESSED", "n-c", "{}", NOW));
        third.markPublished(NOW);
        outboxEventRepository.flush();

        List<OutboxEvent> pending = outboxEventRepository
                .findTop500ByStatusOrderByOutboxIdAsc(OutboxEvent.Status.PENDING);

        assertThat(pending).extracting(OutboxEvent::getOutboxId)
                .containsExactly(first.getOutboxId(), second.getOutboxId());
    }
}
