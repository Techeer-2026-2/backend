package com.techeer.backend.domain.stats.entity;

import com.techeer.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 캠페인별 노출·클릭 집계. Phase 1 한정이며 Phase 3 부터는 ClickHouse(click_facts)로 역할이 옮겨간다.
 */
@Getter
@Entity
@Table(name = "campaign_stats")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampaignStats extends BaseEntity {

    @Id
    private Long campaignId;

    @Column(nullable = false)
    private long sentCount;

    @Column(nullable = false)
    private long clickCount;
}
