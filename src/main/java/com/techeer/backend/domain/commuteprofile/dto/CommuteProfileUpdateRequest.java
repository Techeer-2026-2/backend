package com.techeer.backend.domain.commuteprofile.dto;

import com.techeer.backend.domain.commuteprofile.entity.CommuteType;
import com.techeer.backend.domain.tmap.TransportMode;
import jakarta.validation.Valid;

/**
 * PATCH 요청이라 모든 필드가 선택사항이다. null인 필드는 기존 값을 그대로 유지한다.
 */
public record CommuteProfileUpdateRequest(
        CommuteType commuteType,
        TransportMode transportMode,
        String frequentRouteName,
        @Valid LocationDto departure,
        @Valid LocationDto arrival) {
}
