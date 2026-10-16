package com.techeer.backend.domain.commutesession.dto;

import com.techeer.backend.domain.commutesession.entity.PlaybackAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

@Schema(description = "통근 중 재생 이벤트. 같은 eventId 로 다시 보내도 한 번만 기록된다")
public record PlaybackEventRequest(
        @Schema(description = "앱이 이벤트마다 새로 만드는 UUID(중복 방지 키)", example = "3f2b8c1e-6a0d-4e52-9d3a-1c7f0a9b4e11")
        @NotNull UUID eventId,
        @Schema(description = "곡의 iTunes ID", example = "1440857781")
        @NotNull @Positive Long itunesTrackId,
        @Schema(description = "PLAY(재생) / SKIP(건너뜀) / REPEAT(다시 들음)")
        @NotNull PlaybackAction action,
        @Schema(description = "그 곡을 실제로 들은 시간(초). 모르면 생략", example = "125")
        @Min(0) @Max(86400) Integer playedSeconds) {
}
