package com.techeer.backend.domain.impression.entity;

import com.techeer.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 배너 노출 기록. notification_id 는 노출 1건을 식별하며, 클릭은 이 값으로 노출과 1:0..1 로 연결된다.
 */
@Getter
@Entity
@Table(name = "ad_impressions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdImpression extends BaseEntity {

    @Id
    @Column(length = 36)
    private String notificationId;

    @Column(nullable = false)
    private Long campaignId;

    @Column(nullable = false)
    private Long targetUserId;

    @Column(nullable = false)
    private LocalDateTime shownAt;

    /**
     * 이 노출로 캠페인 예산에서 실제 차감한 금액(원). 날짜별 소진액·CPC·CPM 계산에 쓴다.
     * 컬럼 기본값 0 은 이 컬럼을 모르는 SQL INSERT(기존 테스트 등)가 깨지지 않게 하려는 것이다.
     */
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long chargedAmount;

    /** 노출 당시 유저의 연령대 (10s~50s). 모르면 null. 연령별 CTR 에 쓴다. */
    @Column(length = 16)
    private String ageGroup;

    /** 노출 당시 유저의 성별 (male, female). 모르면 null. */
    @Column(length = 8)
    private String gender;

    /**
     * 빌더에서 전달한 식별자와 노출 시각으로 배너 노출 엔티티를 생성한다.
     *
     * @param notificationId 노출을 식별하는 알림 ID
     * @param campaignId 노출된 캠페인 ID
     * @param targetUserId 노출 대상 사용자 ID
     * @param shownAt 배너가 노출된 시각
     * @param chargedAmount 이 노출로 차감한 금액(원)
     * @param ageGroup 노출 당시 유저 연령대 (모르면 null)
     * @param gender 노출 당시 유저 성별 (모르면 null)
     */
    @Builder
    private AdImpression(String notificationId, Long campaignId, Long targetUserId, LocalDateTime shownAt,
            long chargedAmount, String ageGroup, String gender) {
        this.notificationId = notificationId;
        this.campaignId = campaignId;
        this.targetUserId = targetUserId;
        this.shownAt = shownAt;
        this.chargedAmount = chargedAmount;
        this.ageGroup = ageGroup;
        this.gender = gender;
    }
}
