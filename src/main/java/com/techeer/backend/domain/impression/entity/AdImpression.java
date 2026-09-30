package com.techeer.backend.domain.impression.entity;

import com.techeer.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 배너 노출 기록. notification_id 는 노출 1건을 식별하며, 클릭은 이 값으로 노출과 1:0..1 로 연결된다.
 */
@Getter
@Entity
@Table(name = "ad_impressions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdImpression extends BaseEntity {

    @Id
    @Column(length = 36)
    private String notificationId;

    @Column(nullable = false)
    private Long campaignId;

    @Column(nullable = false)
    private Long targetUserId;

    @Column(nullable = false)
    private LocalDateTime shownAt;

    @Builder
    private AdImpression(String notificationId, Long campaignId, Long targetUserId, LocalDateTime shownAt) {
        this.notificationId = notificationId;
        this.campaignId = campaignId;
        this.targetUserId = targetUserId;
        this.shownAt = shownAt;
    }
}
