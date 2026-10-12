package com.techeer.backend.domain.commuteprofile.dto;

import com.techeer.backend.domain.commuteprofile.entity.CommuteProfile;
import com.techeer.backend.domain.commuteprofile.entity.CommuteType;
import com.techeer.backend.domain.commuteprofile.entity.Location;
import com.techeer.backend.domain.tmap.TransportMode;

public record CommuteProfileResponse(
        Long id,
        CommuteType commuteType,
        TransportMode transportMode,
        String frequentRouteName,
        Integer averageDurationMinutes,
        LocationDto departure,
        LocationDto arrival) {

    public static CommuteProfileResponse from(CommuteProfile profile) {
        return new CommuteProfileResponse(
                profile.getId(),
                profile.getCommuteType(),
                profile.getTransportMode(),
                profile.getFrequentRouteName(),
                profile.getAverageDurationMinutes(),
                toDto(profile.getDeparture()),
                toDto(profile.getArrival()));
    }

    private static LocationDto toDto(Location location) {
        return new LocationDto(location.getLatitude(), location.getLongitude(), location.getPlaceName());
    }
}
