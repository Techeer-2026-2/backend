package com.techeer.backend.domain.stats.service;

import com.techeer.backend.domain.advertiser.repository.AdvertiserRepository;
import com.techeer.backend.domain.campaign.entity.Campaign;
import com.techeer.backend.domain.campaign.repository.CampaignRepository;
import com.techeer.backend.domain.stats.dto.TopCampaignResponse;
import com.techeer.backend.domain.stats.repository.TopCampaignClickRepository;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최근 N분 동안 클릭이 많은 내 캠페인 순위 (고도화 2026-11).
 *
 * <p>클릭 수는 ClickHouse click_facts(분 표)의 실시간 잠정값이다. 캐시 없이 시작한다 (측정 뒤 검토).
 * 기간에 클릭이 없는 캠페인은 순위에 넣지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class TopCampaignService {

    static final int MIN_WINDOW_MINUTES = 1;
    static final int MAX_WINDOW_MINUTES = 60;
    static final int DEFAULT_LIMIT = 5;
    static final int MAX_LIMIT = 20;

    private final AdvertiserRepository advertiserRepository;
    private final CampaignRepository campaignRepository;
    private final TopCampaignClickRepository topCampaignClickRepository;
    private final Clock clock;

    public TopCampaignService(AdvertiserRepository advertiserRepository,
            CampaignRepository campaignRepository,
            TopCampaignClickRepository topCampaignClickRepository,
            Clock clock) {
        this.advertiserRepository = advertiserRepository;
        this.campaignRepository = campaignRepository;
        this.topCampaignClickRepository = topCampaignClickRepository;
        this.clock = clock;
    }

    /**
     * @param advertiserId 로그인한 광고주 ID. 이 광고주의 캠페인만 집계한다
     * @param windowMinutes 지금부터 몇 분 전까지 볼지 (1~60)
     * @param limit 몇 개까지 돌려줄지 (1~20). null 이면 5
     * @throws BusinessException 범위를 벗어난 값(INVALID_INPUT), 없거나 탈퇴한 광고주(ADVERTISER_NOT_FOUND)
     */
    public List<TopCampaignResponse> getTopCampaigns(Long advertiserId, int windowMinutes, Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (windowMinutes < MIN_WINDOW_MINUTES || windowMinutes > MAX_WINDOW_MINUTES
                || size < 1 || size > MAX_LIMIT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (!advertiserRepository.existsByUserIdAndDeletedAtIsNull(advertiserId)) {
            throw new BusinessException(ErrorCode.ADVERTISER_NOT_FOUND);
        }

        Map<Long, Campaign> myCampaigns = campaignRepository
                .findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(advertiserId).stream()
                .collect(Collectors.toMap(Campaign::getCampaignId, Function.identity()));

        LocalDateTime end = LocalDateTime.now(clock);
        LocalDateTime start = end.minusMinutes(windowMinutes);
        Map<Long, Long> topClicks = topCampaignClickRepository.findTopClicks(myCampaigns.keySet(), start, end, size);

        return topClicks.entrySet().stream()
                .map(entry -> new TopCampaignResponse(
                        entry.getKey(), myCampaigns.get(entry.getKey()).getTitle(), entry.getValue()))
                .toList();
    }
}
