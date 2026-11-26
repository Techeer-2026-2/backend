package com.techeer.backend.domain.click.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.backend.domain.click.dto.ClickEventPayload;
import com.techeer.backend.domain.click.dto.ClickResponse;
import com.techeer.backend.domain.impression.entity.AdImpression;
import com.techeer.backend.domain.impression.repository.AdImpressionRepository;
import com.techeer.backend.domain.outbox.EventPublishException;
import com.techeer.backend.domain.outbox.EventPublisher;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 클릭 기록. 요청 경로에서 DB 에 쓰지 않는다. 노출을 읽고 Kafka ad-clicked 로 보내는 것이 전부다.
 * 중복 제거는 하지 않는다 (재계산이 notification_id 로 한 번만 센다).
 */
@Service
@RequiredArgsConstructor
public class ClickService {

    private static final DateTimeFormatter EVENT_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AdImpressionRepository adImpressionRepository;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * @param notificationId 클릭한 노출의 알림 ID
     * @return 전송된 클릭 정보
     * @throws BusinessException 노출이 없으면 IMPRESSION_NOT_FOUND, Kafka 전송이 실패하면 CLICK_PUBLISH_FAILED
     */
    @Transactional(readOnly = true)
    public ClickResponse recordClick(String notificationId) {
        AdImpression impression = adImpressionRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMPRESSION_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now(clock);
        String key = String.valueOf(impression.getCampaignId());
        try {
            eventPublisher.publish(EventPublisher.AD_CLICKED, key, toPayload(impression, now));
        } catch (EventPublishException e) {
            throw new BusinessException(ErrorCode.CLICK_PUBLISH_FAILED);
        }
        return ClickResponse.of(notificationId, impression.getCampaignId(), now);
    }

    private String toPayload(AdImpression impression, LocalDateTime now) {
        try {
            return objectMapper.writeValueAsString(new ClickEventPayload(
                    impression.getNotificationId(),
                    impression.getCampaignId(),
                    orUnknown(impression.getAgeGroup()),
                    orUnknown(impression.getGender()),
                    now.format(EVENT_AT_FORMAT),
                    impression.getTargetUserId()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("클릭 본문 직렬화 실패", e);
        }
    }

    private static String orUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
