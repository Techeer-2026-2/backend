package com.techeer.backend.domain.campaign.repository;

import com.techeer.backend.domain.campaign.entity.Campaign;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    /**
     * 연령대가 맞고 지금 진행 중인 캠페인 1개를 무작위로 고른다.
     *
     * <p>매번 같은 캠페인만 노출되지 않도록 random() 으로 섞는다. 시작·종료 시각이 비어 있으면 제한 없음으로 본다.
     */
    @Query(value = """
            SELECT * FROM campaigns
            WHERE target_age_group = :ageGroup
              AND status = 'ACTIVE'
              AND deleted_at IS NULL
              AND (time_start IS NULL OR time_start <= :now)
              AND (time_end IS NULL OR time_end >= :now)
            ORDER BY random()
            LIMIT 1
            """, nativeQuery = true)
    Optional<Campaign> findRandomActiveByAgeGroup(
            @Param("ageGroup") String ageGroup,
            @Param("now") LocalDateTime now);

    /**
     * 광고주의 지난 캠페인을 최근에 끝난 순으로 조회한다.
     *
     * <p>한 번도 시작하지 않은 초안(DRAFT)은 제외하고, 상태가 ENDED 이거나 종료 시각이 이미 지난 캠페인만 지난 캠페인으로 본다.
     * 종료 시각이 없는 ENDED 캠페인은 맨 뒤에 둔다. 삭제(soft delete)한 캠페인은 제외한다.
     */
    @Query(value = """
            select c from Campaign c
            where c.userId = :userId
              and c.deletedAt is null
              and c.status <> com.techeer.backend.domain.campaign.entity.CampaignStatus.DRAFT
              and (c.status = com.techeer.backend.domain.campaign.entity.CampaignStatus.ENDED
                   or c.timeEnd < :now)
            order by c.timeEnd desc nulls last, c.campaignId desc
            """,
            countQuery = """
            select count(c) from Campaign c
            where c.userId = :userId
              and c.deletedAt is null
              and c.status <> com.techeer.backend.domain.campaign.entity.CampaignStatus.DRAFT
              and (c.status = com.techeer.backend.domain.campaign.entity.CampaignStatus.ENDED
                   or c.timeEnd < :now)
            """)
    Page<Campaign> findHistory(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now,
            Pageable pageable);
}
