package com.techeer.backend.domain.recommendation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param commuteProfileId 어떤 통근 프로필 기준으로 만들지 (출발지 날씨와 출근/퇴근 구분에 쓴다)
 * @param name 플레이리스트 이름. 없으면 상황에 맞춰 자동으로 짓는다
 * @param size 곡 수. 없으면 통근 경로 소요시간에 맞춰 곡 수를 정하고, 소요시간을 모르면 15곡
 * @param latitude 지금 위치의 위도. 주변 인기곡 가산점의 기준이다. 없으면 통근 프로필 출발지를 쓴다. 경도와 함께 보내야 한다
 * @param longitude 지금 위치의 경도. 위치는 이번 요청에서만 쓰고 저장하지 않는다
 */
public record PlaylistGenerateRequest(
        @NotNull Long commuteProfileId,
        @Size(max = 100) String name,
        @Min(1) @Max(50) Integer size,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude) {

    /** 위치 없이 요청할 때(통근 프로필 출발지를 쓴다). */
    public PlaylistGenerateRequest(Long commuteProfileId, String name, Integer size) {
        this(commuteProfileId, name, size, null, null);
    }
}
