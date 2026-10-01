package com.techeer.backend.domain.click.service;

import com.techeer.backend.domain.click.dto.ClickResponse;
import com.techeer.backend.domain.click.dto.ClickResult;
import com.techeer.backend.domain.click.entity.ClickEvent;
import com.techeer.backend.domain.click.repository.ClickEventRepository;
import com.techeer.backend.domain.impression.entity.AdImpression;
import com.techeer.backend.domain.impression.repository.AdImpressionRepository;
import com.techeer.backend.domain.stats.repository.CampaignStatsRepository;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 클릭 기록 (Phase 1: 원본 저장과 집계 UPDATE 를 한 트랜잭션에서 동기 처리).
 *
 * <p>Phase 3 에서는 집계 UPDATE 대신 Kafka click-events 토픽에 발행하고, ClickHouse 가 구독해 롤업한다.
 */
@Service
@RequiredArgsConstructor
public class ClickService {

    private final AdImpressionRepository adImpressionRepository;
    private final ClickEventRepository clickEventRepository;
    private final CampaignStatsRepository campaignStatsRepository;

    /**
     * 노출의 클릭을 멱등하게 기록하고 새 클릭일 때만 캠페인 클릭 수를 증가시킨다.
     * 클릭 저장과 집계 갱신은 하나의 트랜잭션에서 처리한다.
     *
     * @param notificationId 클릭한 노출의 알림 ID
     * @return 최초 기록된 클릭 정보와 이번 요청의 신규 생성 여부
     * @throws BusinessException 알림 ID에 해당하는 노출이 없는 경우
     * @throws IllegalStateException 저장 또는 중복 확인 후 클릭을 조회할 수 없는 경우
     */
    @Transactional
    public ClickResult recordClick(String notificationId) {
        AdImpression impression = adImpressionRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMPRESSION_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        boolean created = clickEventRepository.insertIfAbsent(
                notificationId, impression.getCampaignId(), impression.getTargetUserId(), now) == 1;

        // 중복 클릭은 집계에 반영하지 않는다.
        if (created) {
            campaignStatsRepository.increaseClickCount(impression.getCampaignId(), now);
        }

        ClickEvent clickEvent = clickEventRepository.findByNotificationId(notificationId)
                .orElseThrow(() -> new IllegalStateException("기록된 클릭을 찾을 수 없습니다: " + notificationId));
        return new ClickResult(ClickResponse.from(clickEvent), created);
    }
}
