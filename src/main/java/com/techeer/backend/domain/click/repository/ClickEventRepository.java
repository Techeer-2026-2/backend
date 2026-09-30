package com.techeer.backend.domain.click.repository;

import com.techeer.backend.domain.click.entity.ClickEvent;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    Optional<ClickEvent> findByNotificationId(String notificationId);

    /**
     * 같은 notification_id 가 없을 때만 클릭을 기록한다.
     *
     * <p>조회 후 저장하면 동시 요청이 둘 다 통과할 수 있어서, UNIQUE 제약과 ON CONFLICT 로 DB 가 중복을 막게 한다.
     *
     * @return 1 이면 새로 기록, 0 이면 이미 기록된 클릭
     */
    @Modifying
    @Query(value = """
            INSERT INTO click_events (notification_id, campaign_id, target_user_id, clicked_at, created_at, updated_at)
            VALUES (:notificationId, :campaignId, :targetUserId, :clickedAt, :clickedAt, :clickedAt)
            ON CONFLICT (notification_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("notificationId") String notificationId,
            @Param("campaignId") Long campaignId,
            @Param("targetUserId") Long targetUserId,
            @Param("clickedAt") LocalDateTime clickedAt);
}
