package com.techeer.backend.domain.outbox.repository;

import com.techeer.backend.domain.outbox.entity.OutboxEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** 미발행 행을 outbox_id 순으로 최대 500건. 발행 순서를 맞추기 위해 id 순서를 쓴다. */
    List<OutboxEvent> findTop500ByStatusOrderByOutboxIdAsc(OutboxEvent.Status status);

    /** 발행이 끝난 지 오래된 행을 지운다. 반환값은 지운 행 수. */
    @Modifying
    @Query("delete from OutboxEvent o where o.status = :status and o.publishedAt < :before")
    int deletePublishedBefore(@Param("status") OutboxEvent.Status status, @Param("before") LocalDateTime before);
}
