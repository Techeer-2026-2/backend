package com.techeer.backend.domain.campaign.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Schema(description = "캠페인 수정 요청. 바꿀 필드만 보낸다. "
        + "진행중인 캠페인은 title, body, imageUrl 만 보낼 수 있다.")
public record CampaignUpdateRequest(
        @Schema(description = "배너 제목", example = "OOO 컴백 D-1")
        @Size(min = 1, max = 128)
        @Pattern(regexp = NOT_BLANK, message = BLANK_MESSAGE)
        String title,
        @Schema(description = "배너 본문", example = "내일 오후 6시 신곡 공개")
        @Size(min = 1)
        @Pattern(regexp = NOT_BLANK, message = BLANK_MESSAGE)
        String body,
        @Schema(description = "배너 이미지 URL", example = "https://example.com/banner-v2.png")
        @Size(min = 1, max = 512)
        @Pattern(regexp = NOT_BLANK, message = BLANK_MESSAGE)
        String imageUrl,
        @Schema(description = "클릭 시 이동할 URL. 빈 문자열을 보내면 링크를 지운다.",
                example = "https://example.com/comeback")
        @Size(max = 512)
        String linkUrl,
        @Schema(description = "배너를 노출할 타겟 연령대", example = "20s")
        @Size(min = 1, max = 16)
        @Pattern(regexp = NOT_BLANK, message = BLANK_MESSAGE)
        String targetAgeGroup,
        @Schema(description = "노출 시작 시각", example = "2026-10-07T00:00:00")
        LocalDateTime startAt,
        @Schema(description = "노출 종료 시각. 시작 시각보다 뒤여야 한다.", example = "2026-10-10T18:00:00")
        LocalDateTime endAt) {

    // null 은 "바꾸지 않음" 이라 @NotBlank 대신 값이 있을 때만 공백 여부를 검사한다. 본문은 줄바꿈을 포함할 수 있다.
    private static final String NOT_BLANK = "(?s).*\\S.*";
    private static final String BLANK_MESSAGE = "공백일 수 없습니다";

    /**
     * 진행중에는 바꿀 수 없는 필드(링크·타겟 연령대·노출 기간)를 담고 있는지.
     */
    public boolean changesDelivery() {
        return linkUrl != null || targetAgeGroup != null || startAt != null || endAt != null;
    }
}
