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
import java.util.Optional;
import java.util.UUID;
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
     * @return 매칭되는 진행 중 캠페인이 없으면 empty
     */
    @Transactional
    public Optional<BannerResponse> matchBanner(Long targetUserId) {
        TargetUser targetUser = targetUserRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TARGET_USER_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now(clock);
        return campaignRepository.findRandomActiveByAgeGroup(targetUser.getAgeGroup(), now)
                .map(campaign -> recordImpression(campaign, targetUser, now));
    }

    private BannerResponse recordImpression(Campaign campaign, TargetUser targetUser, LocalDateTime now) {
        String notificationId = UUID.randomUUID().toString();
        adImpressionRepository.save(AdImpression.builder()
                .notificationId(notificationId)
                .campaignId(campaign.getCampaignId())
                .targetUserId(targetUser.getTargetUserId())
                .shownAt(now)
                .build());
        campaignStatsRepository.increaseSentCount(campaign.getCampaignId(), now);
        return BannerResponse.of(notificationId, campaign);
    }
}
