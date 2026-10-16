package com.techeer.backend.domain.playlist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "조회자의 현재 셀에서 같은 플레이리스트를 듣는 인원. 이름이나 개별 위치는 담지 않는다")
public record ActiveListenersResponse(
        @Schema(description = "플레이리스트 ID", example = "12") Long playlistId,
        @Schema(description = "조회자 위치가 속한 H3 셀(해상도 8)", example = "8830e1d8d3fffff") String h3Cell,
        @Schema(description = "그 셀에서 지금 듣는 고유 회원 수(마지막 신호 60초 이내, 위치 30초 이내)", example = "3") long activeListenerCount,
        @Schema(description = "집계 기준 시각") Instant asOf) {
}
