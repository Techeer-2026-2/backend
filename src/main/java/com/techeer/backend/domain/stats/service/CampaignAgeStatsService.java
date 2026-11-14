package com.techeer.backend.domain.stats.service;

import com.techeer.backend.domain.campaign.entity.Campaign;
import com.techeer.backend.domain.campaign.repository.CampaignRepository;
import com.techeer.backend.domain.click.repository.ClickEventRepository;
import com.techeer.backend.domain.impression.repository.AdImpressionRepository;
import com.techeer.backend.domain.stats.dto.CampaignAgeStats;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final AdImpressionRepository adImpressionRepository;
    private final ClickEventRepository clickEventRepository;
    private final Clock clock;

    public CampaignAgeStatsService(CampaignRepository campaignRepository,
            AdImpressionRepository adImpressionRepository,
            ClickEventRepository clickEventRepository,
            Clock clock) {
        this.campaignRepository = campaignRepository;
        this.adImpressionRepository = adImpressionRepository;
        this.clickEventRepository = clickEventRepository;
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

        Map<String, Long> impressions = toCounts(adImpressionRepository.countByAgeGroup(campaignId, start, end));
        Map<String, Long> clicks = toCounts(clickEventRepository.countByAgeGroup(campaignId, start, end));

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

    /** DB 의 null 연령대는 "unknown" 키로 옮긴다. */
    private static Map<String, Long> toCounts(List<Object[]> rows) {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            String age = row[0] == null ? UNKNOWN : row[0].toString();
            counts.merge(age, ((Number) row[1]).longValue(), Long::sum);
        }
        return counts;
    }
}
