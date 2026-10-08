package com.techeer.backend.domain.commuteprofile.dto;

import com.techeer.backend.domain.commuteprofile.entity.CommuteType;
import com.techeer.backend.domain.tmap.TransportMode;

public record CommuteProfileUpdateRequest(
        CommuteType commuteType,
        TransportMode transportMode,
        String frequentRouteName,
        LocationDto departure,
        LocationDto arrival) {
}
