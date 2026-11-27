package com.techeer.backend.domain.outbox.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 아웃박스: 노출 이벤트를 DB 트랜잭션과 같이 기록해 두고, 릴레이가 Kafka 로 보낸 뒤 PUBLISHED 로 바꾼다. */
@Entity
@Table(name = "outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    public enum Status { PENDING, PUBLISHED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long outboxId;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "notification_id", nullable = false, length = 36)
    private String notificationId;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    private OutboxEvent(String eventType, String notificationId, String payload, LocalDateTime createdAt) {
        this.eventType = eventType;
        this.notificationId = notificationId;
        this.payload = payload;
        this.status = Status.PENDING;
        this.createdAt = createdAt;
    }

    public static OutboxEvent create(String eventType, String notificationId, String payload, LocalDateTime now) {
        return new OutboxEvent(eventType, notificationId, payload, now);
    }

    public void markPublished(LocalDateTime now) {
        this.status = Status.PUBLISHED;
        this.publishedAt = now;
    }
}
