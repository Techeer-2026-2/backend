package com.techeer.backend.domain.outbox.repository;

import com.techeer.backend.domain.outbox.entity.OutboxEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** 미발행 행을 outbox_id 순으로 최대 500건. 발행 순서를 맞추기 위해 id 순서를 쓴다. */
    List<OutboxEvent> findTop500ByStatusOrderByOutboxIdAsc(OutboxEvent.Status status);
}
