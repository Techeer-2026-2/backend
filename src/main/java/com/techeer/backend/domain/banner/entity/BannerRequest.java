package com.techeer.backend.domain.banner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 배너 요청 1건의 기록. request_id 는 클라이언트가 보낸 값(재시도 때 같은 값)이라 직접 넣는다.
 * 같은 request_id 가 다시 오면 PK 충돌로 감지해 차감·노출을 하지 않는다.
 * 003 SQL 과 열을 맞추기 위해 BaseEntity(updated_at, deleted_at)를 상속하지 않는다.
 */
@Entity
@Table(name = "banner_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BannerRequest {

    @Id
    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    /** 이 요청으로 나간 노출. 후보가 없었으면 null. */
    @Column(name = "notification_id", length = 36)
    private String notificationId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private BannerRequest(String requestId, Long targetUserId, LocalDateTime createdAt) {
        this.requestId = requestId;
        this.targetUserId = targetUserId;
        this.createdAt = createdAt;
    }

    public static BannerRequest create(String requestId, Long targetUserId, LocalDateTime now) {
        return new BannerRequest(requestId, targetUserId, now);
    }

    public void assignNotification(String notificationId) {
        this.notificationId = notificationId;
    }
}
