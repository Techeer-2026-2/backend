package com.techeer.backend.domain.click.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.backend.domain.click.dto.ClickEventPayload;
import com.techeer.backend.domain.click.dto.ClickResponse;
import com.techeer.backend.domain.click.dto.ClickResult;
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
    private final ClickFallbackService clickFallbackService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Kafka 로 보내고, 실패하면 실패 표에 보관한다 (202). 보관도 실패하면 503.
     *
     * @param notificationId 클릭한 노출의 알림 ID
     * @return 전송됐는지(201) 보관됐는지(202) 여부와 클릭 정보
     * @throws BusinessException 노출이 없으면 IMPRESSION_NOT_FOUND, 보관까지 실패하면 CLICK_PUBLISH_FAILED
     */
    // 트랜잭션을 걸지 않는다. 실패 보관(INSERT)은 ClickFallbackService 가 자기 트랜잭션에서 한다.
    public ClickResult recordClick(String notificationId) {
        AdImpression impression = adImpressionRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMPRESSION_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now(clock);
        String key = String.valueOf(impression.getCampaignId());
        String payload = toPayload(impression, now);
        ClickResponse click = ClickResponse.of(notificationId, impression.getCampaignId(), now);
        try {
            eventPublisher.publish(EventPublisher.AD_CLICKED, key, payload);
            return new ClickResult(click, true);
        } catch (EventPublishException e) {
            try {
                clickFallbackService.save(notificationId, payload);
            } catch (RuntimeException saveFailure) {
                throw new BusinessException(ErrorCode.CLICK_PUBLISH_FAILED);
            }
            return new ClickResult(click, false);
        }
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
