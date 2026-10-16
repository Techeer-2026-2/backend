package com.techeer.backend.domain.playlist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "재생 중 신호 접수 결과")
public record HeartbeatResponse(
        @Schema(description = "이 위치가 속한 H3 셀(해상도 8)", example = "8830e1d8d3fffff") String h3Cell,
        @Schema(description = "다음 신호가 없으면 청취 인원 집계에서 빠지는 시각") Instant activeUntil) {
}
