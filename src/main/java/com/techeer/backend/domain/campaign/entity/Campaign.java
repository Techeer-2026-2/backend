package com.techeer.backend.domain.campaign.entity;

import com.techeer.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 광고 캠페인. 배너는 target_age_group 이 유저의 연령대와 같을 때 노출된다.
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

    @Column(nullable = false, length = 16)
    private String targetAgeGroup;

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
            String targetAgeGroup, LocalDateTime timeStart, LocalDateTime timeEnd, CampaignStatus status) {
        this.userId = userId;
        this.title = title;
        this.body = body;
        this.imageUrl = imageUrl;
        this.linkUrl = linkUrl;
        this.targetAgeGroup = targetAgeGroup;
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
     * 도착 링크·타겟 연령대·노출 기간을 바꾼다. null 인 값은 그대로 두고, 빈 linkUrl 은 링크를 지운다.
     */
    public void updateDelivery(String linkUrl, String targetAgeGroup, LocalDateTime timeStart,
            LocalDateTime timeEnd) {
        if (linkUrl != null) {
            this.linkUrl = linkUrl.isBlank() ? null : linkUrl;
        }
        if (targetAgeGroup != null) {
            this.targetAgeGroup = targetAgeGroup;
        }
        if (timeStart != null) {
            this.timeStart = timeStart;
        }
        if (timeEnd != null) {
            this.timeEnd = timeEnd;
        }
    }
}
