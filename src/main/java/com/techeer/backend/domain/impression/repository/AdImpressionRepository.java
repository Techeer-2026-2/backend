package com.techeer.backend.domain.impression.repository;

import com.techeer.backend.domain.impression.entity.AdImpression;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdImpressionRepository extends JpaRepository<AdImpression, String> {

    /**
     * 캠페인의 노출을 연령대별로 센다. 연령을 모르면 age_group 이 null 인 행으로 나온다.
     *
     * @return 각 행은 [연령대(String 또는 null), 노출 수(Number)]
     */
    @Query(value = """
            SELECT age_group, COUNT(*) AS cnt
            FROM ad_impressions
            WHERE campaign_id = :campaignId AND shown_at >= :from AND shown_at < :to
            GROUP BY age_group
            """, nativeQuery = true)
    List<Object[]> countByAgeGroup(
            @Param("campaignId") Long campaignId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
