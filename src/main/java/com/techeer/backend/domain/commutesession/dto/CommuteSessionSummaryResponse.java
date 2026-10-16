package com.techeer.backend.domain.commutesession.dto;

import com.techeer.backend.domain.commutesession.entity.CommuteSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "통근 결과 요약(곡·시간). 진행 중인 통근에도 지금까지의 값을 돌려준다")
public record CommuteSessionSummaryResponse(
        @Schema(description = "세션 ID", example = "5") Long sessionId,
        CommuteSessionStatus status,
        OffsetDateTime startedAt,
        @Schema(description = "종료·취소 시각. 진행 중이면 null") OffsetDateTime endedAt,
        @Schema(description = "시작할 때의 예상 소요시간(분). 모르면 null") Integer expectedDurationMinutes,
        @Schema(description = "실제 통근 시간(초). 진행 중이면 시작부터 지금까지", example = "1920") long commuteSeconds,
        @Schema(description = "한 번이라도 재생한 곡 수", example = "8") int playedTrackCount,
        @Schema(description = "곡을 들은 시간의 합(초)", example = "1650") long totalPlayedSeconds,
        @Schema(description = "건너뛴 횟수", example = "2") int skipCount,
        @Schema(description = "곡별 요약. 들은 시간이 긴 순서") List<TrackSummary> tracks) {

    @Schema(description = "곡 하나의 요약. 곡 정보를 모르는 곡(우리 곡 목록에 없음)은 이름·이미지가 null")
    public record TrackSummary(
            Long itunesTrackId,
            String trackName,
            String artistName,
            String artworkUrl,
            int playCount,
            int skipCount,
            int repeatCount,
            long playedSeconds) {
    }
}
