package com.techeer.backend.domain.campaign.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Schema(description = "캠페인 등록 요청")
public record CampaignCreateRequest(
        @Schema(description = "컴백 광고 대상 아티스트 ID", example = "12")
        @NotNull
        Long artistId,
        @Schema(description = "배너 제목", example = "OOO 컴백 D-3")
        @NotBlank
        @Size(max = 128)
        String title,
        @Schema(description = "배너 본문", example = "10월 10일 오후 6시 신곡 공개")
        @NotBlank
        String body,
        @Schema(description = "배너 이미지 URL", example = "https://example.com/banner.png")
        @NotBlank
        @Size(max = 512)
        String imageUrl,
        @Schema(description = "클릭 시 이동할 URL. 비우면 배너 탭 자체를 클릭으로 집계한다.",
                example = "https://example.com/comeback")
        @Size(max = 512)
        String linkUrl,
        @Schema(description = "배너를 노출할 타겟 연령대", example = "20s")
        @NotBlank
        @Size(max = 16)
        String targetAgeGroup,
        @Schema(description = "노출 시작 시각", example = "2026-10-07T00:00:00")
        @NotNull
        LocalDateTime startAt,
        @Schema(description = "노출 종료 시각. 시작 시각보다 뒤여야 한다.", example = "2026-10-10T18:00:00")
        @NotNull
        LocalDateTime endAt) {
}
