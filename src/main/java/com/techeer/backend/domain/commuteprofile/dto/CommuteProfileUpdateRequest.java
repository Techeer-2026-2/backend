package com.techeer.backend.domain.commuteprofile.dto;

import com.techeer.backend.domain.commuteprofile.entity.CommuteType;
import com.techeer.backend.domain.tmap.TransportMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CommuteProfileUpdateRequest(
        @NotNull CommuteType commuteType,
        @NotNull TransportMode transportMode,
        @NotBlank String frequentRouteName,
        @Valid @NotNull LocationDto departure,
        @Valid @NotNull LocationDto arrival) {
}
