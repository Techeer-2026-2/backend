package com.techeer.backend.domain.click.entity;

import com.techeer.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 클릭 원본. notification_id UNIQUE 로 같은 노출에 대한 클릭은 한 번만 기록된다(멱등성).
 *
 * <p>저장은 ClickEventRepository#insertIfAbsent 로만 한다.
 */
@Getter
@Entity
@Table(name = "click_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClickEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long clickId;

    @Column(nullable = false, unique = true, length = 36)
    private String notificationId;

    @Column(nullable = false)
    private Long campaignId;

    @Column(nullable = false)
    private Long targetUserId;

    @Column(nullable = false)
    private LocalDateTime clickedAt;
}
