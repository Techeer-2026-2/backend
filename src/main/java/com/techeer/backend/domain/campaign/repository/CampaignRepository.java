package com.techeer.backend.domain.campaign.repository;

import com.techeer.backend.domain.campaign.entity.Campaign;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
}
