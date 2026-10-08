package com.techeer.backend.domain.commuteprofile.service;

import com.techeer.backend.domain.commuteprofile.dto.CommuteProfileCreateRequest;
import com.techeer.backend.domain.commuteprofile.dto.CommuteProfileResponse;
import com.techeer.backend.domain.commuteprofile.dto.CommuteProfileUpdateRequest;
import com.techeer.backend.domain.commuteprofile.dto.LocationDto;
import com.techeer.backend.domain.commuteprofile.entity.CommuteProfile;
import com.techeer.backend.domain.commuteprofile.entity.Location;
import com.techeer.backend.domain.commuteprofile.repository.CommuteProfileRepository;
import com.techeer.backend.domain.tmap.TransportMode;
import com.techeer.backend.domain.tmap.service.TmapDurationService;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommuteProfileService {

    private final CommuteProfileRepository commuteProfileRepository;
    private final TmapDurationService tmapDurationService;

    @Transactional
    public CommuteProfileResponse create(Long memberId, CommuteProfileCreateRequest request) {
        Location departure = toLocation(request.departure());
        Location arrival = toLocation(request.arrival());
        int averageDurationMinutes = calculateDuration(departure, arrival, request.transportMode());

        CommuteProfile profile = CommuteProfile.builder()
                .memberId(memberId)
                .commuteType(request.commuteType())
                .transportMode(request.transportMode())
                .frequentRouteName(request.frequentRouteName())
                .averageDurationMinutes(averageDurationMinutes)
                .departure(departure)
                .arrival(arrival)
                .build();

        return CommuteProfileResponse.from(commuteProfileRepository.save(profile));
    }

    public List<CommuteProfileResponse> findAll(Long memberId) {
        return commuteProfileRepository.findAllByMemberId(memberId).stream()
                .map(CommuteProfileResponse::from)
                .toList();
    }

    public CommuteProfileResponse findDetail(Long memberId, Long profileId) {
        return CommuteProfileResponse.from(findOwnedOrThrow(memberId, profileId));
    }

    @Transactional
    public CommuteProfileResponse update(Long memberId, Long profileId, CommuteProfileUpdateRequest request) {
        CommuteProfile profile = findOwnedOrThrow(memberId, profileId);
        Location departure = toLocation(request.departure());
        Location arrival = toLocation(request.arrival());
        int averageDurationMinutes = calculateDuration(departure, arrival, request.transportMode());

        profile.update(
                request.commuteType(),
                request.transportMode(),
                request.frequentRouteName(),
                averageDurationMinutes,
                departure,
                arrival);

        return CommuteProfileResponse.from(profile);
    }

    @Transactional
    public void delete(Long memberId, Long profileId) {
        commuteProfileRepository.delete(findOwnedOrThrow(memberId, profileId));
    }

    private int calculateDuration(Location departure, Location arrival, TransportMode transportMode) {
        return tmapDurationService.getDurationMinutes(
                departure.getLongitude(), departure.getLatitude(),
                arrival.getLongitude(), arrival.getLatitude(),
                transportMode);
    }

    private Location toLocation(LocationDto dto) {
        return new Location(dto.latitude(), dto.longitude(), dto.placeName());
    }

    private CommuteProfile findOwnedOrThrow(Long memberId, Long profileId) {
        CommuteProfile profile = commuteProfileRepository.findById(profileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMUTE_PROFILE_NOT_FOUND));
        if (!profile.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.COMMUTE_PROFILE_NOT_FOUND);
        }
        return profile;
    }
}
