package com.techeer.backend.domain.campaign.service;

import com.techeer.backend.domain.campaign.dto.CampaignCreateRequest;
import com.techeer.backend.domain.campaign.dto.CampaignProgress;
import com.techeer.backend.domain.campaign.dto.CampaignResponse;
import com.techeer.backend.domain.campaign.entity.Campaign;
import com.techeer.backend.domain.campaign.entity.CampaignStatus;
import com.techeer.backend.domain.campaign.repository.CampaignRepository;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캠페인 등록·목록 조회. 대기/진행중/종료는 저장하지 않고 노출 기간으로 계산한다.
 */
@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final Clock clock;

    /**
     * 캠페인을 등록한다. 배너 매칭 대상이 되도록 ACTIVE 로 저장하며, 실제 노출은 노출 기간 안에서만 일어난다.
     *
     * @throws BusinessException 종료 시각이 시작 시각보다 뒤가 아닌 경우
     */
    @Transactional
    public CampaignResponse createCampaign(Long userId, CampaignCreateRequest request) {
        if (!request.endAt().isAfter(request.startAt())) {
            throw new BusinessException(ErrorCode.INVALID_CAMPAIGN_PERIOD);
        }

        Campaign campaign = campaignRepository.save(Campaign.builder()
                .userId(userId)
                .title(request.title())
                .body(request.body())
                .imageUrl(request.imageUrl())
                .linkUrl(request.linkUrl())
                .targetAgeGroup(request.targetAgeGroup())
                .timeStart(request.startAt())
                .timeEnd(request.endAt())
                .status(CampaignStatus.ACTIVE)
                .build());
        return CampaignResponse.of(campaign, LocalDateTime.now(clock));
    }

    /**
     * @param progress 조회할 진행 상태. null 이면 전체
     * @return 최근 등록순 캠페인 목록
     */
    @Transactional(readOnly = true)
    public List<CampaignResponse> getMyCampaigns(Long userId, CampaignProgress progress) {
        LocalDateTime now = LocalDateTime.now(clock);
        return campaignRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId).stream()
                .map(campaign -> CampaignResponse.of(campaign, now))
                .filter(campaign -> progress == null || campaign.status() == progress)
                .toList();
    }
}
