package com.techeer.backend.domain.banner.service;

import com.techeer.backend.domain.banner.dto.BannerResponse;
import com.techeer.backend.domain.campaign.entity.Campaign;
import com.techeer.backend.domain.campaign.repository.CampaignRepository;
import com.techeer.backend.domain.impression.entity.AdImpression;
import com.techeer.backend.domain.impression.repository.AdImpressionRepository;
import com.techeer.backend.domain.stats.repository.CampaignStatsRepository;
import com.techeer.backend.domain.targetuser.entity.TargetUser;
import com.techeer.backend.domain.targetuser.repository.TargetUserRepository;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연령대 매칭 배너 노출. 배너를 돌려줄 때마다 노출 1건(notification_id)을 기록한다.
 */
@Service
@RequiredArgsConstructor
public class BannerService {

    private final TargetUserRepository targetUserRepository;
    private final CampaignRepository campaignRepository;
    private final AdImpressionRepository adImpressionRepository;
    private final CampaignStatsRepository campaignStatsRepository;
    private final Clock clock;

    /**
     * 후보 중 입찰가에 비례한 확률로 1개를 골라 예산을 차감하고 노출을 기록한다.
     * 고른 캠페인의 예산이 그 사이 모자라졌으면 그 캠페인을 빼고 다시 고른다.
     *
     * @return 매칭되는, 예산이 남은 진행 중 캠페인이 없으면 empty
     */
    @Transactional
    public Optional<BannerResponse> matchBanner(Long targetUserId) {
        TargetUser targetUser = targetUserRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TARGET_USER_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now(clock);
        List<Campaign> candidates = new ArrayList<>(campaignRepository.findActiveForTarget(
                targetUser.getAgeGroup(), toGenderCode(targetUser.getGender()), now));
        while (!candidates.isEmpty()) {
            Campaign winner = BidAuction.pick(candidates, ThreadLocalRandom.current().nextDouble());
            long charge = BidAuction.charge(winner, candidates);
            if (campaignRepository.deductBudget(winner.getCampaignId(), charge) == 1) {
                return Optional.of(recordImpression(winner, targetUser, charge, now));
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private BannerResponse recordImpression(Campaign campaign, TargetUser targetUser, long charge,
            LocalDateTime now) {
        String notificationId = UUID.randomUUID().toString();
        adImpressionRepository.save(AdImpression.builder()
                .notificationId(notificationId)
                .campaignId(campaign.getCampaignId())
                .targetUserId(targetUser.getTargetUserId())
                .shownAt(now)
                .chargedAmount(charge)
                .ageGroup(blankToNull(targetUser.getAgeGroup()))
                .gender(blankToNull(toGenderCode(targetUser.getGender())))
                .build());
        campaignStatsRepository.increaseSentCount(campaign.getCampaignId(), now);
        return BannerResponse.of(notificationId, campaign);
    }
}
