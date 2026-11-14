package com.techeer.backend.domain.campaign.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "캠페인 등록 요청. 배너 이미지는 이 JSON 이 아니라 multipart 의 image 파트로 보낸다.")
public record CampaignCreateRequest(
        @Schema(description = "배너 제목", example = "OOO 컴백 D-3")
        @NotBlank
        @Size(max = 128)
        String title,
        @Schema(description = "배너 본문", example = "10월 10일 오후 6시 신곡 공개")
        @NotBlank
        String body,
        @Schema(description = "클릭 시 이동할 URL. 비우면 배너 탭 자체를 클릭으로 집계한다.",
                example = "https://example.com/comeback")
        @Size(max = 512)
        String linkUrl,
        @Schema(description = "노출할 타겟 연령대 목록 (여러 개 가능). 값: 10s, 20s, 30s, 40s, 50s. "
                + "유저의 연령대가 이 목록에 있어야 노출된다.",
                example = "[\"20s\", \"30s\"]")
        @NotEmpty
        List<String> targetAgeGroups,
        @Schema(description = "노출할 타겟 성별 목록. 값: male, female, all(전체). "
                + "all 은 단독으로만 보낼 수 있다. 유저의 성별이 이 목록에 있어야 노출된다.",
                example = "[\"female\"]")
        @NotEmpty
        List<String> targetGenders,
        @Schema(description = "노출 시작 시각", example = "2026-10-07T00:00:00")
        @NotNull
        LocalDateTime startAt,
        @Schema(description = "노출 종료 시각. 시작 시각보다 뒤여야 한다.", example = "2026-10-10T18:00:00")
        @NotNull
        LocalDateTime endAt,
        @Schema(description = "총 예산(원). 누적 소진액이 이 금액에 닿으면 노출이 멈춘다.", example = "100000")
        @NotNull
        @Positive
        Long totalBudget,
        @Schema(description = "입찰가(원). 노출 1회당 최대로 낼 금액이며 총 예산보다 클 수 없다. "
                + "실제 차감액은 경쟁 상황에 따라 이보다 작을 수 있다.", example = "50")
        @NotNull
        @Positive
        Long bidPrice,
        @Schema(description = "목표 클릭수", example = "300")
        @NotNull
        @Positive
        Long targetClicks) {
}
