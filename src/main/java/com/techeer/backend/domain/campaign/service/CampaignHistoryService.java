package com.techeer.backend.domain.campaign.service;

import com.techeer.backend.domain.advertiser.repository.AdvertiserRepository;
import com.techeer.backend.domain.campaign.dto.CampaignHistoryItem;
import com.techeer.backend.domain.campaign.entity.Campaign;
import com.techeer.backend.domain.campaign.repository.CampaignRepository;
import com.techeer.backend.domain.stats.entity.CampaignStats;
import com.techeer.backend.domain.stats.repository.CampaignStatsRepository;
import com.techeer.backend.global.dto.PageResponse;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CampaignHistoryService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private final AdvertiserRepository advertiserRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignStatsRepository campaignStatsRepository;
    private final Clock clock;

    /**
     * 광고주의 지난 캠페인과 노출·클릭 결과를 최근에 끝난 순으로 돌려준다.
     *
     * <p>캠페인 목록을 먼저 한 페이지만 가져오고, 그 캠페인들의 집계는 한 번의 쿼리로 모아서 붙인다.
     * 캠페인마다 집계를 따로 조회하면 페이지 크기만큼 쿼리가 더 나가기 때문이다(N+1).
     * page 는 0 미만이면 0 으로, size 는 1~100 범위로 보정한다. 큰 값을 그대로 받으면 한 번에 너무 많이 읽게 된다.
     *
     * @param advertiserId 광고주 ID
     * @param page 페이지 번호(0부터)
     * @param size 페이지 크기
     * @return 지난 캠페인 목록 페이지
     * @throws BusinessException 없거나 탈퇴한 광고주인 경우
     */
    @Transactional(readOnly = true)
    public PageResponse<CampaignHistoryItem> getHistory(Long advertiserId, int page, int size) {
        if (!advertiserRepository.existsByUserIdAndDeletedAtIsNull(advertiserId)) {
            throw new BusinessException(ErrorCode.ADVERTISER_NOT_FOUND);
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Campaign> campaigns = campaignRepository.findHistory(
                advertiserId, LocalDateTime.now(clock), PageRequest.of(safePage, safeSize));

        List<Long> campaignIds = campaigns.getContent().stream().map(Campaign::getCampaignId).toList();
        Map<Long, CampaignStats> statsByCampaignId = campaignStatsRepository.findAllById(campaignIds).stream()
                .collect(Collectors.toMap(CampaignStats::getCampaignId, Function.identity()));

        return PageResponse.from(campaigns.map(campaign -> {
            CampaignStats stats = statsByCampaignId.get(campaign.getCampaignId());
            return CampaignHistoryItem.of(
                    campaign,
                    stats == null ? 0 : stats.getSentCount(),
                    stats == null ? 0 : stats.getClickCount());
        }));
    }
}
