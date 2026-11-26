package com.techeer.backend.domain.outbox.service;

import com.techeer.backend.domain.outbox.entity.OutboxEvent;
import com.techeer.backend.domain.outbox.repository.OutboxEventRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 아웃박스 행의 상태를 바꾸는 짧은 트랜잭션. 릴레이가 행마다 따로 호출해서 한 행의 실패가 다른 행에 번지지 않는다. */
@Service
@RequiredArgsConstructor
public class OutboxStatusService {

    private final OutboxEventRepository outboxEventRepository;
    private final Clock clock;

    @Transactional
    public void markPublished(Long outboxId) {
        OutboxEvent event = outboxEventRepository.findById(outboxId).orElseThrow();
        event.markPublished(LocalDateTime.now(clock));
    }

    @Transactional
    public int deletePublishedBefore(LocalDateTime before) {
        return outboxEventRepository.deletePublishedBefore(OutboxEvent.Status.PUBLISHED, before);
    }
}
