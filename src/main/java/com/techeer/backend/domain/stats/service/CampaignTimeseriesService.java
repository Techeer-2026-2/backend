package com.techeer.backend.domain.stats.service;

import com.techeer.backend.domain.campaign.entity.Campaign;
import com.techeer.backend.domain.campaign.repository.CampaignRepository;
import com.techeer.backend.domain.click.repository.ClickEventRepository;
import com.techeer.backend.domain.impression.repository.AdImpressionRepository;
import com.techeer.backend.domain.stats.dto.CampaignTimeseriesBucket;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캠페인의 노출·클릭·소진액 추이와 CTR·CPC·CPM 을 시간 또는 일 단위로 만든다.
 * 노출과 클릭은 각각 원본 표(ad_impressions, click_events)를 센다. 기록이 없는 구간은 0으로 채운다.
 */
@Service
@Transactional(readOnly = true)
public class CampaignTimeseriesService {

    /** 기본 조회 기간: 최근 7일 */
    private static final int DEFAULT_DAYS = 7;
    /** 한 번에 만들 구간 수 상한 (시간 단위 31일 = 744, 일 단위 1년 = 366) */
    private static final int MAX_BUCKETS = 800;

    private final CampaignRepository campaignRepository;
    private final AdImpressionRepository adImpressionRepository;
    private final ClickEventRepository clickEventRepository;
    private final Clock clock;

    public CampaignTimeseriesService(CampaignRepository campaignRepository,
            AdImpressionRepository adImpressionRepository,
            ClickEventRepository clickEventRepository,
            Clock clock) {
        this.campaignRepository = campaignRepository;
        this.adImpressionRepository = adImpressionRepository;
        this.clickEventRepository = clickEventRepository;
        this.clock = clock;
    }

    /**
     * @param advertiserId 로그인한 광고주 ID
     * @param campaignId 조회할 캠페인 ID (본인 캠페인만 가능)
     * @param from 조회 시작 (포함). null 이면 기준 시각에서 7일 전
     * @param to 조회 끝 (제외). null 이면 지금
     * @param granularity "hour" 또는 "day". null 이면 day
     */
    public List<CampaignTimeseriesBucket> getTimeseries(Long advertiserId, Long campaignId,
            LocalDateTime from, LocalDateTime to, String granularity) {
        findOwnedCampaign(advertiserId, campaignId);

        LocalDateTime end = to != null ? to : LocalDateTime.now(clock);
        LocalDateTime start = from != null ? from : end.minusDays(DEFAULT_DAYS);
        if (!start.isBefore(end)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String unit = normalizeUnit(granularity);
        ChronoUnit step = "hour".equals(unit) ? ChronoUnit.HOURS : ChronoUnit.DAYS;

        LocalDateTime firstBucket = start.truncatedTo(step);
        long bucketCount = step.between(firstBucket, end) + 1;
        if (bucketCount > MAX_BUCKETS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        Map<LocalDateTime, Long> impressions = toCounts(adImpressionRepository.countByBucket(campaignId, unit, start, end));
        Map<LocalDateTime, Long> clicks = toCounts(clickEventRepository.countByBucket(campaignId, unit, start, end));
        Map<LocalDateTime, Long> spent = toCounts(adImpressionRepository.sumChargedByBucket(campaignId, unit, start, end));

        java.util.ArrayList<CampaignTimeseriesBucket> result = new java.util.ArrayList<>();
        for (LocalDateTime bucket = firstBucket; bucket.isBefore(end); bucket = bucket.plus(1, step)) {
            result.add(CampaignTimeseriesBucket.of(
                    bucket,
                    impressions.getOrDefault(bucket, 0L),
                    clicks.getOrDefault(bucket, 0L),
                    spent.getOrDefault(bucket, 0L)));
        }
        return result;
    }

    private void findOwnedCampaign(Long advertiserId, Long campaignId) {
        Campaign campaign = campaignRepository.findByCampaignIdAndDeletedAtIsNull(campaignId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND));
        if (!campaign.getUserId().equals(advertiserId)) {
            throw new BusinessException(ErrorCode.CAMPAIGN_ACCESS_DENIED);
        }
    }

    private static String normalizeUnit(String granularity) {
        if (granularity == null || granularity.isBlank()) {
            return "day";
        }
        String unit = granularity.trim().toLowerCase(java.util.Locale.ROOT);
        if (!unit.equals("hour") && !unit.equals("day")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return unit;
    }

    private static Map<LocalDateTime, Long> toCounts(List<Object[]> rows) {
        Map<LocalDateTime, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            LocalDateTime bucket = ((Timestamp) row[0]).toLocalDateTime();
            counts.put(bucket, ((Number) row[1]).longValue());
        }
        return counts;
    }
}
