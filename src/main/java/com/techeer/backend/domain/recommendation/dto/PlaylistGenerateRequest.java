package com.techeer.backend.domain.recommendation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param commuteProfileId 어떤 통근 프로필 기준으로 만들지 (출발지 날씨와 출근/퇴근 구분에 쓴다)
 * @param name 플레이리스트 이름. 없으면 상황에 맞춰 자동으로 짓는다
 * @param size 곡 수. 없으면 15곡
 */
public record PlaylistGenerateRequest(
        @NotNull Long commuteProfileId,
        @Size(max = 100) String name,
        @Min(1) @Max(50) Integer size) {
}
