package com.techeer.backend.domain.stats.service;

import com.techeer.backend.domain.campaign.entity.Campaign;
import com.techeer.backend.domain.campaign.repository.CampaignRepository;
import com.techeer.backend.domain.stats.dto.CampaignAgeStats;
import com.techeer.backend.domain.stats.repository.StatsFactsRepository;
import com.techeer.backend.domain.stats.repository.StatsFactsRepository.Fact;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캠페인의 노출·클릭을 연령대별로 나눈다. 연령대 순서는 10s~50s, 그다음 unknown(모름).
 */
@Service
@Transactional(readOnly = true)
public class CampaignAgeStatsService {

    private static final List<String> AGE_GROUPS = List.of("10s", "20s", "30s", "40s", "50s");
    private static final String UNKNOWN = "unknown";
    private static final int DEFAULT_DAYS = 7;

    private final CampaignRepository campaignRepository;
    private final StatsFactsRepository statsFactsRepository;
    private final Clock clock;

    public CampaignAgeStatsService(CampaignRepository campaignRepository,
            StatsFactsRepository statsFactsRepository,
            Clock clock) {
        this.campaignRepository = campaignRepository;
        this.statsFactsRepository = statsFactsRepository;
        this.clock = clock;
    }

    public List<CampaignAgeStats> getByAge(Long advertiserId, Long campaignId,
            LocalDateTime from, LocalDateTime to) {
        Campaign campaign = campaignRepository.findByCampaignIdAndDeletedAtIsNull(campaignId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND));
        if (!campaign.getUserId().equals(advertiserId)) {
            throw new BusinessException(ErrorCode.CAMPAIGN_ACCESS_DENIED);
        }
        LocalDateTime end = to != null ? to : LocalDateTime.now(clock);
        LocalDateTime start = from != null ? from : end.minusDays(DEFAULT_DAYS);
        if (!start.isBefore(end)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 노출, 클릭 수는 ClickHouse 분 표에서 읽는다. 연령을 모르는 기록은 "unknown" 으로 저장되어 있다.
        Map<String, Long> impressions = statsFactsRepository.countByAgeGroup(Fact.IMPRESSION, campaignId, start, end);
        Map<String, Long> clicks = statsFactsRepository.countByAgeGroup(Fact.CLICK, campaignId, start, end);

        List<CampaignAgeStats> result = new ArrayList<>();
        for (String age : AGE_GROUPS) {
            result.add(CampaignAgeStats.of(age, impressions.getOrDefault(age, 0L), clicks.getOrDefault(age, 0L)));
        }
        long unknownImpressions = impressions.getOrDefault(UNKNOWN, 0L);
        long unknownClicks = clicks.getOrDefault(UNKNOWN, 0L);
        if (unknownImpressions > 0 || unknownClicks > 0) {
            result.add(CampaignAgeStats.of(UNKNOWN, unknownImpressions, unknownClicks));
        }
        return result;
    }

}
