package com.techeer.backend.domain.click.entity;

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

/** Kafka 발행이 실패한 클릭 보관. 재전송기가 Kafka 가 살아나면 ad-clicked 로 보내고 SENT 로 바꾼다. */
@Entity
@Table(name = "click_fallback")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClickFallback {

    public enum Status { PENDING, SENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fallback_id")
    private Long fallbackId;

    @Column(name = "notification_id", nullable = false, length = 36)
    private String notificationId;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    private ClickFallback(String notificationId, String payload, LocalDateTime createdAt) {
        this.notificationId = notificationId;
        this.payload = payload;
        this.status = Status.PENDING;
        this.createdAt = createdAt;
    }

    public static ClickFallback create(String notificationId, String payload, LocalDateTime now) {
        return new ClickFallback(notificationId, payload, now);
    }

    public void markSent(LocalDateTime now) {
        this.status = Status.SENT;
        this.sentAt = now;
    }
}
