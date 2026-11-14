package com.techeer.backend.domain.click.repository;

import com.techeer.backend.domain.click.entity.ClickEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    /**
     * 알림 ID에 대해 최초로 기록된 클릭을 조회한다.
     *
     * @param notificationId 클릭한 노출의 알림 ID
     * @return 기록된 클릭, 없으면 빈 Optional
     */
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

    /**
     * 캠페인의 클릭을 시간(hour) 또는 일(day) 구간별로 센다.
     *
     * @param unit date_trunc 단위 ("hour" 또는 "day")
     * @return 각 행은 [구간 시작 시각(Timestamp), 클릭 수(Number)]
     */
    @Query(value = """
            SELECT date_trunc(CAST(:unit AS text), clicked_at) AS bucket, COUNT(*) AS cnt
            FROM click_events
            WHERE campaign_id = :campaignId AND clicked_at >= :from AND clicked_at < :to
            GROUP BY bucket
            """, nativeQuery = true)
    List<Object[]> countByBucket(
            @Param("campaignId") Long campaignId,
            @Param("unit") String unit,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * 캠페인의 클릭을 노출의 연령대별로 센다. 클릭은 notification_id 로 노출과 연결한다.
     *
     * @return 각 행은 [연령대(String 또는 null), 클릭 수(Number)]
     */
    @Query(value = """
            SELECT i.age_group, COUNT(*) AS cnt
            FROM click_events c
            JOIN ad_impressions i ON i.notification_id = c.notification_id
            WHERE c.campaign_id = :campaignId AND c.clicked_at >= :from AND c.clicked_at < :to
            GROUP BY i.age_group
            """, nativeQuery = true)
    List<Object[]> countByAgeGroup(
            @Param("campaignId") Long campaignId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
