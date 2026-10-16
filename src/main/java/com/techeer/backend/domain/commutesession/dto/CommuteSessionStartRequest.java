package com.techeer.backend.domain.commutesession.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "통근 시작 요청")
public record CommuteSessionStartRequest(
        @Schema(description = "어느 통근 프로필로 출발하는지. 예상 소요시간을 이 프로필의 경로에서 가져온다", example = "1")
        @NotNull Long commuteProfileId,
        @Schema(description = "통근 중 들을 플레이리스트(선택). 내 플레이리스트여야 한다", example = "12")
        Long playlistId) {
}
