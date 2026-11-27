package com.techeer.backend.domain.banner.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.backend.domain.banner.dto.BannerResponse;
import com.techeer.backend.domain.banner.entity.BannerRequest;
import com.techeer.backend.domain.banner.repository.BannerRequestRepository;
import com.techeer.backend.domain.campaign.entity.Campaign;
import com.techeer.backend.domain.campaign.repository.CampaignRepository;
import com.techeer.backend.domain.impression.entity.AdImpression;
import com.techeer.backend.domain.impression.repository.AdImpressionRepository;
import com.techeer.backend.domain.outbox.dto.AdEventPayload;
import com.techeer.backend.domain.outbox.entity.OutboxEvent;
import com.techeer.backend.domain.outbox.repository.OutboxEventRepository;
import com.techeer.backend.domain.stats.repository.CampaignStatsRepository;
import com.techeer.backend.domain.user.entity.User;
import com.techeer.backend.domain.user.repository.UserRepository;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연령대 매칭 배너 노출. 로그인한 회원의 연령대·성별로 매칭하고, 노출 1건(notification_id)을 기록한다.
 * 같은 트랜잭션에서 Kafka 발행용 아웃박스 행을 남기고, 발행은 릴레이가 나중에 한다.
 * 같은 요청 ID 의 재시도는 차감·노출 없이 저장된 결과를 돌려준다.
 */
@Service
@RequiredArgsConstructor
public class BannerService {

    private static final DateTimeFormatter EVENT_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserRepository userRepository;
    private final CampaignRepository campaignRepository;
    private final AdImpressionRepository adImpressionRepository;
    private final CampaignStatsRepository campaignStatsRepository;
    private final BannerRequestRepository bannerRequestRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 같은 요청 ID 의 재시도면 차감·노출 없이 저장된 결과를 돌려준다.
     * 처음 요청이면 후보 중 입찰가에 비례한 확률로 1개를 골라 예산을 차감하고 노출을 기록한다.
     *
     * @param memberId  배너를 볼 음악 앱 회원 ID (users.user_id)
     * @param requestId 요청 ID (재시도는 같은 값)
     * @return 매칭되는, 예산이 남은 진행 중 캠페인이 없거나 회원이 연령대를 입력하지 않았으면 empty
     * @throws BusinessException 없거나 탈퇴한 회원인 경우
     */
    @Transactional
    public Optional<BannerResponse> matchBanner(Long memberId, String requestId) {
        User user = userRepository.findByUserIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        // 연령대는 모든 캠페인의 필수 타겟 조건이라, 모르면 맞는 캠페인이 없다.
        if (user.getAgeGroup() == null) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        // 요청 ID 를 먼저 넣는다. 이미 있으면 재시도이므로 저장된 결과를 돌려준다.
        if (bannerRequestRepository.insertIfAbsent(requestId, user.getUserId(), now) == 0) {
            return replay(requestId);
        }

        List<Campaign> candidates = new ArrayList<>(campaignRepository.findActiveForTarget(
                user.getAgeGroup(), toGenderCode(user.getGender()), now));
        while (!candidates.isEmpty()) {
            Campaign winner = BidAuction.pick(candidates, ThreadLocalRandom.current().nextDouble());
            long charge = BidAuction.charge(winner, candidates);
            if (campaignRepository.deductBudget(winner.getCampaignId(), charge) == 1) {
                return Optional.of(recordImpression(requestId, winner, user, charge, now));
            }
            candidates.remove(winner);
        }
        return Optional.empty();
    }

    /**
     * 유저에 저장된 성별 값을 캠페인 성별 코드(male, female)로 맞춘다. 알 수 없는 값이면 빈 문자열.
     *
     * <p>기존 데이터는 F/M 으로 저장돼 있을 수 있어서 둘 다 받는다.
     */
    static String toGenderCode(String gender) {
        if (gender == null) {
            return "";
        }
        return switch (gender.trim().toLowerCase()) {
            case "m", "male" -> "male";
            case "f", "female" -> "female";
            default -> "";
        };
    }

    private Optional<BannerResponse> replay(String requestId) {
        BannerRequest previous = bannerRequestRepository.findById(requestId).orElseThrow();
        if (previous.getNotificationId() == null) {
            return Optional.empty();
        }
        AdImpression impression = adImpressionRepository.findById(previous.getNotificationId()).orElseThrow();
        Campaign campaign = campaignRepository.findById(impression.getCampaignId()).orElseThrow();
        return Optional.of(BannerResponse.of(impression.getNotificationId(), campaign));
    }

    private BannerResponse recordImpression(String requestId, Campaign campaign, User user, long charge,
            LocalDateTime now) {
        String notificationId = UUID.randomUUID().toString();
        String ageGroup = orUnknown(user.getAgeGroup());
        String gender = orUnknown(toGenderCode(user.getGender()));

        adImpressionRepository.save(AdImpression.builder()
                .notificationId(notificationId)
                .campaignId(campaign.getCampaignId())
                .userId(user.getUserId())
                .shownAt(now)
                .chargedAmount(charge)
                .ageGroup(blankToNull(ageGroup))
                .gender(blankToNull(gender))
                .build());
        campaignStatsRepository.increaseSentCount(campaign.getCampaignId(), now);
        outboxEventRepository.save(OutboxEvent.create("AD_IMPRESSED", notificationId,
                toPayload(notificationId, campaign, ageGroup, gender, now), now));
        bannerRequestRepository.findById(requestId).orElseThrow().assignNotification(notificationId);
        return BannerResponse.of(notificationId, campaign);
    }

    private String toPayload(String notificationId, Campaign campaign, String ageGroup, String gender,
            LocalDateTime now) {
        try {
            return objectMapper.writeValueAsString(new AdEventPayload(
                    notificationId, campaign.getCampaignId(), ageGroup, gender, now.format(EVENT_AT_FORMAT)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("이벤트 본문 직렬화 실패", e);
        }
    }

    private static String orUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() || "unknown".equals(value) ? null : value;
    }
}
