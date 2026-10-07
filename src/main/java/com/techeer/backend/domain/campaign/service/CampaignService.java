package com.techeer.backend.domain.campaign.service;

import com.techeer.backend.domain.campaign.dto.CampaignCreateRequest;
import com.techeer.backend.domain.campaign.dto.CampaignDetailResponse;
import com.techeer.backend.domain.campaign.dto.CampaignProgress;
import com.techeer.backend.domain.campaign.dto.CampaignResponse;
import com.techeer.backend.domain.campaign.dto.CampaignUpdateRequest;
import com.techeer.backend.domain.campaign.entity.Campaign;
import com.techeer.backend.domain.campaign.entity.CampaignStatus;
import com.techeer.backend.domain.campaign.repository.CampaignRepository;
import com.techeer.backend.domain.stats.repository.CampaignStatsRepository;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캠페인 등록·조회·수정·삭제. 대기/진행중/종료는 저장하지 않고 노출 기간으로 계산한다.
 */
@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignStatsRepository campaignStatsRepository;
    private final Clock clock;

    /**
     * 캠페인을 등록한다. 배너 매칭 대상이 되도록 ACTIVE 로 저장하며, 실제 노출은 노출 기간 안에서만 일어난다.
     *
     * @throws BusinessException 종료 시각이 시작 시각보다 뒤가 아니거나 이미 지난 경우
     */
    @Transactional
    public CampaignResponse createCampaign(Long userId, CampaignCreateRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        validatePeriod(request.startAt(), request.endAt(), now);

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
        return CampaignResponse.of(campaign, now);
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

    /**
     * 캠페인 1건을 노출수·클릭수와 함께 돌려준다. 아직 노출된 적이 없으면 둘 다 0 이다.
     *
     * @throws BusinessException 캠페인이 없거나 삭제된 경우, 내 캠페인이 아닌 경우
     */
    @Transactional(readOnly = true)
    public CampaignDetailResponse getCampaign(Long userId, Long campaignId) {
        Campaign campaign = findMyCampaign(userId, campaignId);
        return toDetail(campaign, LocalDateTime.now(clock));
    }

    /**
     * 요청에 담긴 필드만 수정한다. 대기 상태는 전체, 진행중은 문구와 배너 이미지만 수정할 수 있고 종료 후에는 수정할 수 없다.
     *
     * @throws BusinessException 캠페인이 없거나 내 캠페인이 아닌 경우, 현재 상태에서 바꿀 수 없는 필드를 보낸 경우,
     *         수정 결과 종료 시각이 시작 시각보다 뒤가 아니거나 이미 지난 경우
     */
    @Transactional
    public CampaignDetailResponse updateCampaign(Long userId, Long campaignId, CampaignUpdateRequest request) {
        Campaign campaign = checkOwner(userId,
                campaignRepository.findWithLockByCampaignIdAndDeletedAtIsNull(campaignId));
        LocalDateTime now = LocalDateTime.now(clock);

        CampaignProgress progress = CampaignProgress.of(campaign.getTimeStart(), campaign.getTimeEnd(), now);
        if (progress == CampaignProgress.ENDED) {
            throw new BusinessException(ErrorCode.CAMPAIGN_ENDED);
        }
        if (progress == CampaignProgress.ONGOING && request.changesDelivery()) {
            throw new BusinessException(ErrorCode.CAMPAIGN_FIELD_NOT_EDITABLE);
        }

        campaign.updateContent(request.title(), request.body(), request.imageUrl());
        campaign.updateDelivery(request.linkUrl(), request.targetAgeGroup(), request.startAt(), request.endAt());
        validatePeriod(campaign.getTimeStart(), campaign.getTimeEnd(), now);

        // 응답의 updatedAt 에 이번 수정 시각이 담기도록 바로 반영한다.
        campaignRepository.flush();
        return toDetail(campaign, now);
    }

    /**
     * 캠페인을 soft delete 한다. 노출·클릭 기록과 집계는 남고, 배너 매칭과 목록에서는 빠진다.
     *
     * @throws BusinessException 캠페인이 없거나 이미 삭제된 경우, 내 캠페인이 아닌 경우
     */
    @Transactional
    public void deleteCampaign(Long userId, Long campaignId) {
        Campaign campaign = findMyCampaign(userId, campaignId);
        campaign.delete(LocalDateTime.now(clock));
    }

    private Campaign findMyCampaign(Long userId, Long campaignId) {
        return checkOwner(userId, campaignRepository.findByCampaignIdAndDeletedAtIsNull(campaignId));
    }

    private Campaign checkOwner(Long userId, Optional<Campaign> found) {
        Campaign campaign = found.orElseThrow(() -> new BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND));
        if (!campaign.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.CAMPAIGN_ACCESS_DENIED);
        }
        return campaign;
    }

    /**
     * 시작·종료 시각이 비어 있으면(기간 제한 없는 기존 캠페인) 해당 검사는 건너뛴다.
     * 시작 시각은 과거여도 된다 (등록·수정 즉시 노출 시작).
     */
    private void validatePeriod(LocalDateTime timeStart, LocalDateTime timeEnd, LocalDateTime now) {
        if (timeEnd == null) {
            return;
        }
        if (timeStart != null && !timeEnd.isAfter(timeStart)) {
            throw new BusinessException(ErrorCode.INVALID_CAMPAIGN_PERIOD);
        }
        if (!timeEnd.isAfter(now)) {
            throw new BusinessException(ErrorCode.CAMPAIGN_PERIOD_ALREADY_ENDED);
        }
    }

    private CampaignDetailResponse toDetail(Campaign campaign, LocalDateTime now) {
        return campaignStatsRepository.findById(campaign.getCampaignId())
                .map(stats -> CampaignDetailResponse.of(campaign, stats.getSentCount(), stats.getClickCount(), now))
                .orElseGet(() -> CampaignDetailResponse.of(campaign, 0, 0, now));
    }
}
