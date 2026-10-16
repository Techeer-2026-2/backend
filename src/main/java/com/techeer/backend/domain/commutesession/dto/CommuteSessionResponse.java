package com.techeer.backend.domain.commutesession.dto;

import com.techeer.backend.domain.commutesession.entity.CommuteSession;
import com.techeer.backend.domain.commutesession.entity.CommuteSessionStatus;
import com.techeer.backend.global.config.ClockConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "통근 세션. 프론트는 startedAt 과 expectedDurationMinutes 로 남은 시간·진행률을 계산할 수 있다")
public record CommuteSessionResponse(
        @Schema(description = "세션 ID", example = "5") Long id,
        @Schema(description = "ACTIVE(진행 중) / COMPLETED(종료) / CANCELED(취소)") CommuteSessionStatus status,
        @Schema(description = "통근 프로필 ID", example = "1") Long commuteProfileId,
        @Schema(description = "플레이리스트 ID. 정하지 않았으면 null", example = "12") Long playlistId,
        @Schema(description = "시작 시각(한국 시간대 오프셋 포함)") OffsetDateTime startedAt,
        @Schema(description = "종료·취소 시각. 진행 중이면 null") OffsetDateTime endedAt,
        @Schema(description = "시작할 때의 예상 소요시간(분). 통근 경로를 모르면 null", example = "32")
        Integer expectedDurationMinutes,
        @Schema(description = "예상 도착 시각. 예상 소요시간을 모르면 null") OffsetDateTime expectedArrivalAt) {

    public static CommuteSessionResponse from(CommuteSession session) {
        OffsetDateTime startedAt = session.getStartedAt().atZone(ClockConfig.SEOUL).toOffsetDateTime();
        Integer expected = session.getExpectedDurationMinutes();
        return new CommuteSessionResponse(
                session.getId(),
                session.getStatus(),
                session.getCommuteProfileId(),
                session.getPlaylistId(),
                startedAt,
                session.getEndedAt() == null ? null : session.getEndedAt().atZone(ClockConfig.SEOUL).toOffsetDateTime(),
                expected,
                expected == null ? null : startedAt.plusMinutes(expected));
    }
}
