package com.techeer.backend.domain.campaign.dto;

import java.time.LocalDateTime;

/**
 * 노출 기간으로 계산한 캠페인 진행 상태. DB 에 저장하지 않고 조회 시점의 시각으로 판정한다.
 */
public enum CampaignProgress {
    PENDING,
    ONGOING,
    ENDED;

    /**
     * 시작·종료 시각이 비어 있으면 제한 없음으로 본다 (배너 매칭 쿼리와 같은 기준).
     */
    public static CampaignProgress of(LocalDateTime timeStart, LocalDateTime timeEnd, LocalDateTime now) {
        if (timeStart != null && timeStart.isAfter(now)) {
            return PENDING;
        }
        if (timeEnd != null && timeEnd.isBefore(now)) {
            return ENDED;
        }
        return ONGOING;
    }
}
