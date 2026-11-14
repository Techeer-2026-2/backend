package com.techeer.backend.domain.impression.repository;

import com.techeer.backend.domain.impression.entity.AdImpression;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdImpressionRepository extends JpaRepository<AdImpression, String> {

    /**
     * 캠페인의 노출당 실제 차감액을 시간(hour) 또는 일(day) 구간별로 더한다.
     *
     * @param unit date_trunc 단위 ("hour" 또는 "day")
     * @return 각 행은 [구간 시작 시각(Timestamp), 소진액 합(Number)]
     */
    @Query(value = """
            SELECT date_trunc(CAST(:unit AS text), shown_at) AS bucket, SUM(charged_amount) AS spent
            FROM ad_impressions
            WHERE campaign_id = :campaignId AND shown_at >= :from AND shown_at < :to
            GROUP BY bucket
            """, nativeQuery = true)
    List<Object[]> sumChargedByBucket(
            @Param("campaignId") Long campaignId,
            @Param("unit") String unit,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * 캠페인의 노출을 시간(hour) 또는 일(day) 구간별로 센다.
     *
     * @param unit date_trunc 단위 ("hour" 또는 "day")
     * @return 각 행은 [구간 시작 시각(Timestamp), 노출 수(Number)]
     */
    @Query(value = """
            SELECT date_trunc(CAST(:unit AS text), shown_at) AS bucket, COUNT(*) AS cnt
            FROM ad_impressions
            WHERE campaign_id = :campaignId AND shown_at >= :from AND shown_at < :to
            GROUP BY bucket
            """, nativeQuery = true)
    List<Object[]> countByBucket(
            @Param("campaignId") Long campaignId,
            @Param("unit") String unit,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
