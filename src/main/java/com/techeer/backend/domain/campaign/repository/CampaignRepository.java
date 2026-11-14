package com.techeer.backend.domain.campaign.repository;

import com.techeer.backend.domain.campaign.entity.Campaign;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    List<Campaign> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);

    Optional<Campaign> findByCampaignIdAndDeletedAtIsNull(Long campaignId);

    /**
     * 수정용 조회. 행을 잠가서 동시에 들어온 수정이 서로의 변경을 덮어쓰지 않게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Campaign> findWithLockByCampaignIdAndDeletedAtIsNull(Long campaignId);

    /**
     * 유저의 연령대와 성별이 둘 다 맞고, 지금 진행 중이며, 예산이 남은 캠페인을 모두 고른다 (배너 매칭 후보).
     *
     * <p>연령대는 campaign_age_groups 에 있어야 하고, 성별은 campaign_genders 에 있거나 all 이어야 한다.
     * 유저 성별을 모르면 gender 는 빈 문자열로 넘기며, 그 경우 성별 제한이 없는 캠페인만 맞는다.
     * 시작·종료 시각이 비어 있으면 제한 없음으로 본다. 어떤 후보를 노출할지는 입찰가로 서비스에서 고른다.
     *
     * <p>매칭되는 연령대·성별 행을 중복으로 세지 않도록 EXISTS 로 확인한다.
     */
    @Query(value = """
            SELECT * FROM campaigns c
            WHERE c.status = 'ACTIVE'
              AND c.deleted_at IS NULL
              AND c.spent_total < c.total_budget
              AND (c.time_start IS NULL OR c.time_start <= :now)
              AND (c.time_end IS NULL OR c.time_end >= :now)
              AND EXISTS (
                  SELECT 1 FROM campaign_age_groups a
                  WHERE a.campaign_id = c.campaign_id AND a.age_group = :ageGroup)
              AND EXISTS (
                  SELECT 1 FROM campaign_genders g
                  WHERE g.campaign_id = c.campaign_id AND g.gender IN ('all', :gender))
            ORDER BY c.campaign_id
            """, nativeQuery = true)
    List<Campaign> findActiveForTarget(
            @Param("ageGroup") String ageGroup,
            @Param("gender") String gender,
            @Param("now") LocalDateTime now);

    /**
     * 예산이 충분할 때만 소진액을 늘린다. 조건 검사와 증가가 한 문장이라 동시에 요청이 와도 예산을 넘지 않는다.
     *
     * @return 차감했으면 1, 예산이 모자라 차감하지 못했으면 0
     */
    @Modifying
    @Query(value = """
            UPDATE campaigns
            SET spent_total = spent_total + :amount
            WHERE campaign_id = :campaignId
              AND spent_total + :amount <= total_budget
            """, nativeQuery = true)
    int deductBudget(@Param("campaignId") Long campaignId, @Param("amount") long amount);

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
