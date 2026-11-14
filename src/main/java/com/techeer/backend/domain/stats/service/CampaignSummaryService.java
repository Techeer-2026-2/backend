package com.techeer.backend.domain.stats.service;

import com.techeer.backend.domain.campaign.entity.Campaign;
import com.techeer.backend.domain.campaign.repository.CampaignRepository;
import com.techeer.backend.domain.stats.client.ClaudeMessageClient;
import com.techeer.backend.domain.stats.dto.CampaignSummaryResponse;
import com.techeer.backend.domain.stats.entity.CampaignStats;
import com.techeer.backend.domain.stats.repository.CampaignStatsRepository;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캠페인의 누적 노출·클릭 숫자만 Claude 에 보내고, 요약 문장을 돌려준다.
 * 배너 제목·문구 같은 내용은 보내지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class CampaignSummaryService {

    private final CampaignRepository campaignRepository;
    private final CampaignStatsRepository campaignStatsRepository;
    private final ClaudeMessageClient claudeMessageClient;

    public CampaignSummaryService(CampaignRepository campaignRepository,
            CampaignStatsRepository campaignStatsRepository,
            ClaudeMessageClient claudeMessageClient) {
        this.campaignRepository = campaignRepository;
        this.campaignStatsRepository = campaignStatsRepository;
        this.claudeMessageClient = claudeMessageClient;
    }

    public CampaignSummaryResponse summarize(Long advertiserId, Long campaignId) {
        Campaign campaign = campaignRepository.findByCampaignIdAndDeletedAtIsNull(campaignId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND));
        if (!campaign.getUserId().equals(advertiserId)) {
            throw new BusinessException(ErrorCode.CAMPAIGN_ACCESS_DENIED);
        }

        // 키가 없으면 외부 호출 전에 막는다
        if (!claudeMessageClient.isConfigured()) {
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED);
        }

        CampaignStats stats = campaignStatsRepository.findById(campaignId).orElse(null);
        long impressions = stats == null ? 0 : stats.getSentCount();
        long clicks = stats == null ? 0 : stats.getClickCount();
        double ctr = impressions > 0 ? (double) clicks / impressions * 100 : 0.0;

        String prompt = String.format(Locale.ROOT,
                "다음은 광고 캠페인의 누적 성과입니다. 노출 %d회, 클릭 %d회, 클릭률 %.2f%%.%n"
                        + "광고주가 이해하기 쉽게 한국어로 2~3문장으로 요약하고, 개선 방향을 한 가지 제안해 주세요. "
                        + "숫자만 근거로 쓰고 없는 사실은 말하지 마세요.",
                impressions, clicks, ctr);

        return new CampaignSummaryResponse(campaignId, claudeMessageClient.complete(prompt));
    }
}
