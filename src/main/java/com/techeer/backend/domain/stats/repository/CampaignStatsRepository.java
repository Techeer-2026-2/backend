package com.techeer.backend.domain.stats.repository;

import com.techeer.backend.domain.stats.entity.CampaignStats;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignStatsRepository extends JpaRepository<CampaignStats, Long> {

    /**
     * click_count 를 1 올린다. 집계 행이 아직 없으면 만든다.
     *
     * <p>읽고-더하고-쓰는 대신 DB 에서 원자적으로 더해서 동시 클릭에도 카운트가 유실되지 않는다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO campaign_stats (campaign_id, sent_count, click_count, created_at, updated_at)
            VALUES (:campaignId, 0, 1, :now, :now)
            ON CONFLICT (campaign_id)
            DO UPDATE SET click_count = campaign_stats.click_count + 1, updated_at = :now
            """, nativeQuery = true)
    void increaseClickCount(@Param("campaignId") Long campaignId, @Param("now") LocalDateTime now);

    /**
     * sent_count(노출 수)를 1 올린다. 집계 행이 아직 없으면 만든다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO campaign_stats (campaign_id, sent_count, click_count, created_at, updated_at)
            VALUES (:campaignId, 1, 0, :now, :now)
            ON CONFLICT (campaign_id)
            DO UPDATE SET sent_count = campaign_stats.sent_count + 1, updated_at = :now
            """, nativeQuery = true)
    void increaseSentCount(@Param("campaignId") Long campaignId, @Param("now") LocalDateTime now);
}
