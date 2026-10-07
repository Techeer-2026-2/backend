package com.techeer.backend.domain.campaign.dto;

import com.techeer.backend.domain.campaign.entity.Campaign;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "지난 캠페인 한 건과 결과 요약")
public record CampaignHistoryItem(
        @Schema(description = "캠페인 ID", example = "10") Long campaignId,
        @Schema(description = "광고 제목", example = "출근길 커피 1+1") String title,
        @Schema(description = "배너 이미지 주소") String imageUrl,
        @Schema(description = "타겟 연령대", example = "20s") String targetAgeGroup,
        @Schema(description = "노출 시작 시각") LocalDateTime startAt,
        @Schema(description = "노출 종료 시각. 종료 시각 없이 ENDED 로 끝난 캠페인은 null") LocalDateTime endAt,
        @Schema(description = "노출 수", example = "120") long sentCount,
        @Schema(description = "클릭 수", example = "30") long clickCount,
        @Schema(description = "클릭률(클릭 수 / 노출 수). 노출이 없으면 0", example = "0.25") double clickRate) {

    /**
     * 캠페인과 집계 값으로 만든다. 집계 행이 없는 캠페인은 노출·클릭 0 을 넘기면 된다.
     *
     * <p>이력에는 끝난 캠페인만 나오므로 진행 상태(status)는 싣지 않는다. 시각 이름은 캠페인 등록·목록 API 와 같게 startAt/endAt 이다.
     */
    public static CampaignHistoryItem of(Campaign campaign, long sentCount, long clickCount) {
        double clickRate = sentCount == 0 ? 0.0 : (double) clickCount / sentCount;
        return new CampaignHistoryItem(
                campaign.getCampaignId(),
                campaign.getTitle(),
                campaign.getImageUrl(),
                campaign.getTargetAgeGroup(),
                campaign.getTimeStart(),
                campaign.getTimeEnd(),
                sentCount,
                clickCount,
                clickRate);
    }
}
