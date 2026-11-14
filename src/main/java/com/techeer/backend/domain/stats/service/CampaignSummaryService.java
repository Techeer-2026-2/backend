package com.techeer.backend.domain.stats.service;

import com.techeer.backend.domain.campaign.entity.Campaign;
import com.techeer.backend.domain.campaign.repository.CampaignRepository;
import com.techeer.backend.domain.stats.client.ClaudeMessageClient;
import com.techeer.backend.domain.stats.dto.CampaignSummaryResponse;
import com.techeer.backend.domain.stats.entity.CampaignStats;
import com.techeer.backend.domain.stats.repository.CampaignStatsRepository;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import com.techeer.backend.domain.click.repository.ClickEventRepository;
import com.techeer.backend.domain.impression.repository.AdImpressionRepository;
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
    private final AdImpressionRepository adImpressionRepository;
    private final ClickEventRepository clickEventRepository;
    private final Clock clock;

    public CampaignSummaryService(CampaignRepository campaignRepository,
            CampaignStatsRepository campaignStatsRepository,
            ClaudeMessageClient claudeMessageClient,
            AdImpressionRepository adImpressionRepository,
            ClickEventRepository clickEventRepository,
            Clock clock) {
        this.campaignRepository = campaignRepository;
        this.campaignStatsRepository = campaignStatsRepository;
        this.claudeMessageClient = claudeMessageClient;
        this.adImpressionRepository = adImpressionRepository;
        this.clickEventRepository = clickEventRepository;
        this.clock = clock;
    }

    /** 나이대별 노출·클릭. 연령을 모르는 노출은 unknown 으로 모은다. */
    private String ageBreakdown(Long campaignId, Campaign campaign, LocalDateTime now) {
        LocalDateTime from = campaign.getTimeStart() != null ? campaign.getTimeStart() : now.minusDays(30);
        LocalDateTime to = now;
        Map<String, long[]> byAge = new TreeMap<>();
        for (Object[] row : adImpressionRepository.countByAgeGroup(campaignId, from, to)) {
            String age = row[0] == null ? "unknown" : row[0].toString();
            byAge.computeIfAbsent(age, k -> new long[2])[0] += ((Number) row[1]).longValue();
        }
        for (Object[] row : clickEventRepository.countByAgeGroup(campaignId, from, to)) {
            String age = row[0] == null ? "unknown" : row[0].toString();
            byAge.computeIfAbsent(age, k -> new long[2])[1] += ((Number) row[1]).longValue();
        }
        if (byAge.isEmpty()) {
            return "연령별 기록 없음";
        }
        StringBuilder sb = new StringBuilder();
        byAge.forEach((age, v) -> sb.append(String.format(Locale.ROOT, "%s: 노출 %d회, 클릭 %d회, 클릭률 %.2f%%; ",
                age, v[0], v[1], v[0] > 0 ? (double) v[1] / v[0] * 100 : 0.0)));
        return sb.toString().trim();
    }

    /**
     * 숫자는 코드에서 계산해서 넣고, 모델에는 문장으로만 쓰게 한다. 마크다운 기호는 쓰지 않게 한다.
     */
    static String buildPrompt(Campaign campaign, long impressions, long clicks, String ageBreakdown, LocalDateTime now) {
        double ctr = impressions > 0 ? (double) clicks / impressions * 100 : 0.0;
        long budget = campaign.getTotalBudget();
        long spent = campaign.getSpentTotal();
        double spentRate = budget > 0 ? (double) spent / budget * 100 : 0.0;
        String status;
        String projection;
        LocalDateTime start = campaign.getTimeStart();
        LocalDateTime end = campaign.getTimeEnd();
        if (start == null || end == null || !end.isAfter(start)) {
            status = "기간 정보 없음";
            projection = "기간 정보가 없어 마감 전망을 계산할 수 없습니다.";
        } else if (now.isBefore(start)) {
            status = "대기";
            projection = "아직 시작 전이라 마감 전망은 없습니다.";
        } else if (now.isAfter(end) || campaign.getStatus() == com.techeer.backend.domain.campaign.entity.CampaignStatus.ENDED) {
            status = "종료";
            projection = String.format(Locale.ROOT, "종료 시점 예산 소진율은 %.1f%%입니다.", spentRate);
        } else {
            status = "진행중";
            double total = java.time.Duration.between(start, end).toMinutes();
            double elapsed = java.time.Duration.between(start, now).toMinutes();
            double ratio = total > 0 ? Math.max(elapsed / total, 0.0001) : 1.0;
            double projectedSpent = spent / ratio;
            double projectedRate = budget > 0 ? projectedSpent / budget * 100 : 0.0;
            projection = String.format(Locale.ROOT,
                    "기간의 %.0f%%가 지났고, 지금 속도가 이어지면 마감 때 예산 소진율은 약 %.0f%%입니다.",
                    ratio * 100, projectedRate);
        }
        return String.format(Locale.ROOT, """
                다음은 광고 캠페인의 현재 성과입니다. 모든 숫자는 이미 계산된 값이니 그대로 쓰세요.
                상태: %s
                누적 노출 %d회, 클릭 %d회, 클릭률 %.2f%%.
                예산 %d원 중 %d원 사용 (소진율 %.1f%%).
                나이대별 성과: %s
                마감 전망: %s

                아래 네 개의 소제목을 이 순서대로 쓰세요. 소제목은 번호 없이 마크다운 제목(## 성과 요약)으로 쓰고, 각 소제목 아래에 한국어 문장으로 답하세요.
                ## 성과 요약: 2~3문장으로 현재 성과를 설명합니다. 노출이 0이면 아직 노출되지 않았다고만 쓰세요.
                ## 개선 방향: 가장 중요한 개선점 한 가지를 제안합니다. 근거가 되는 숫자를 함께 쓰세요.
                ## 마감 전망: 위 마감 전망 문장을 바탕으로 이대로 가면 어떻게 끝날지 한두 문장으로 쓰세요.
                ## 나이대별 특징: 어느 나이대의 노출이나 클릭이 많은지, 클릭률이 높은 나이대가 어디인지 한두 문장으로 쓰세요. 기록이 없으면 없다고만 쓰세요.
                굵은 글씨(**)는 쓰지 마세요. 소제목 외의 마크다운 기호는 최소한만 쓰세요.
                없는 사실은 말하지 마세요.
                """, status, impressions, clicks, ctr, budget, spent, spentRate, ageBreakdown, projection);
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
        LocalDateTime now = LocalDateTime.now(clock);
        String prompt = buildPrompt(campaign, impressions, clicks, ageBreakdown(campaignId, campaign, now), now);
        return new CampaignSummaryResponse(campaignId, claudeMessageClient.complete(prompt));
    }
}
