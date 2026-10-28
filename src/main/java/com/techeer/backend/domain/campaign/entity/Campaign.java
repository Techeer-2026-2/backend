package com.techeer.backend.domain.campaign.entity;

import com.techeer.backend.global.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 광고 캠페인. 배너는 유저의 연령대가 ageGroups 에 있고, 유저의 성별이 genders 에 있을 때 노출된다.
 * 연령대·성별은 여러 개를 고를 수 있어서 별도 테이블(campaign_age_groups, campaign_genders)에 한 줄씩 저장한다.
 */
@Getter
@Entity
@Table(name = "campaigns")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Campaign extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long campaignId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(length = 512)
    private String imageUrl;

    @Column(length = 512)
    private String linkUrl;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "campaign_age_groups", joinColumns = @JoinColumn(name = "campaign_id"))
    @Column(name = "age_group", nullable = false, length = 16)
    private Set<String> ageGroups = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "campaign_genders", joinColumns = @JoinColumn(name = "campaign_id"))
    @Column(name = "gender", nullable = false, length = 8)
    private Set<String> genders = new HashSet<>();

    private LocalDateTime timeStart;

    private LocalDateTime timeEnd;

    // Phase 3 맥락 조건
    @Column(length = 32)
    private String weatherCondition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CampaignStatus status;

    @Builder
    private Campaign(Long userId, String title, String body, String imageUrl, String linkUrl,
            Set<String> ageGroups, Set<String> genders, LocalDateTime timeStart, LocalDateTime timeEnd,
            CampaignStatus status) {
        this.userId = userId;
        this.title = title;
        this.body = body;
        this.imageUrl = imageUrl;
        this.linkUrl = linkUrl;
        this.ageGroups = new HashSet<>(ageGroups);
        this.genders = new HashSet<>(genders);
        this.timeStart = timeStart;
        this.timeEnd = timeEnd;
        this.status = status;
    }

    /**
     * 문구와 배너 이미지를 바꾼다. null 인 값은 그대로 둔다.
     */
    public void updateContent(String title, String body, String imageUrl) {
        if (title != null) {
            this.title = title;
        }
        if (body != null) {
            this.body = body;
        }
        if (imageUrl != null) {
            this.imageUrl = imageUrl;
        }
    }

    /**
     * 도착 링크·타겟 조건·노출 기간을 바꾼다. null 인 값은 그대로 두고, 빈 linkUrl 은 링크를 지운다.
     * 타겟 목록(연령대·성별)은 넘어온 목록으로 통째로 바꾼다.
     */
    public void updateDelivery(String linkUrl, Set<String> ageGroups, Set<String> genders,
            LocalDateTime timeStart, LocalDateTime timeEnd) {
        if (linkUrl != null) {
            this.linkUrl = linkUrl.isBlank() ? null : linkUrl;
        }
        if (ageGroups != null) {
            this.ageGroups.clear();
            this.ageGroups.addAll(ageGroups);
        }
        if (genders != null) {
            this.genders.clear();
            this.genders.addAll(genders);
        }
        if (timeStart != null) {
            this.timeStart = timeStart;
        }
        if (timeEnd != null) {
            this.timeEnd = timeEnd;
        }
    }
}
