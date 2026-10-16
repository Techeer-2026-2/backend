package com.techeer.backend.domain.playlist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

@Schema(description = "재생 중 신호(heartbeat). 앱이 재생 중 약 20초마다 현재 위치와 측정 시각을 보낸다")
public record ListeningHeartbeatRequest(
        @Schema(description = "위도", example = "37.555")
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @Schema(description = "경도", example = "126.923")
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
        @Schema(description = "위치를 측정한 시각(UTC). 30초보다 오래됐거나 5초보다 미래이면 거절한다", example = "2026-10-15T08:00:00Z")
        @NotNull Instant recordedAt) {
}
