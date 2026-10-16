package com.techeer.backend.domain.commutesession.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "재생 이벤트 기록 결과")
public record PlaybackEventResponse(
        @Schema(description = "이벤트 ID") UUID eventId,
        @Schema(description = "이번 요청으로 새로 기록했으면 true, 이미 기록돼 있던 같은 이벤트이면 false") boolean created) {
}
