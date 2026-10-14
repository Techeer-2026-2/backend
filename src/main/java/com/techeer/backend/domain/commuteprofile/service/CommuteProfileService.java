package com.techeer.backend.domain.commuteprofile.service;

import com.techeer.backend.domain.commuteprofile.dto.CommuteProfileCreateRequest;
import com.techeer.backend.domain.commuteprofile.dto.CommuteProfileResponse;
import com.techeer.backend.domain.commuteprofile.dto.CommuteProfileUpdateRequest;
import com.techeer.backend.domain.commuteprofile.dto.LocationDto;
import com.techeer.backend.domain.commuteprofile.entity.CommuteProfile;
import com.techeer.backend.domain.commuteprofile.entity.CommuteRoute;
import com.techeer.backend.domain.commuteprofile.entity.Location;
import com.techeer.backend.domain.commuteprofile.repository.CommuteProfileRepository;
import com.techeer.backend.domain.commuteprofile.repository.CommuteRouteRepository;
import com.techeer.backend.domain.tmap.TransportMode;
import com.techeer.backend.domain.tmap.service.TmapDurationService;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class CommuteProfileService {

    private final CommuteProfileRepository commuteProfileRepository;
    private final CommuteRouteRepository commuteRouteRepository;
    private final TmapDurationService tmapDurationService;
    private final TransactionTemplate transactionTemplate;

    @Transactional
    public CommuteProfileResponse create(Long memberId, CommuteProfileCreateRequest request) {
        Location departure = toLocation(request.departure());
        Location arrival = toLocation(request.arrival());
        int averageDurationMinutes = calculateDuration(departure, arrival, request.transportMode());

        CommuteProfile profile = CommuteProfile.builder()
                .memberId(memberId)
                .commuteType(request.commuteType())
                .frequentRouteName(request.frequentRouteName())
                .departure(departure)
                .arrival(arrival)
                .build();
        commuteProfileRepository.save(profile);

        CommuteRoute route = CommuteRoute.builder()
                .commuteProfile(profile)
                .transportMode(request.transportMode())
                .averageDurationMinutes(averageDurationMinutes)
                .build();
        commuteRouteRepository.save(route);

        return CommuteProfileResponse.from(profile, route);
    }

    @Transactional(readOnly = true)
    public List<CommuteProfileResponse> findAll(Long memberId) {
        return commuteProfileRepository.findAllByMemberId(memberId).stream()
                .map(profile -> CommuteProfileResponse.from(profile, findRouteOrThrow(profile.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CommuteProfileResponse findDetail(Long memberId, Long profileId) {
        CommuteProfile profile = findOwnedOrThrow(memberId, profileId);
        return CommuteProfileResponse.from(profile, findRouteOrThrow(profile.getId()));
    }

    /**
     * TMAP 호출(느릴 수 있음)을 DB 트랜잭션 밖에서 먼저 끝내고, 실제 쓰기(소유권 재확인 + 저장)만
     * 짧은 트랜잭션으로 묶는다. 읽기 → 외부 호출 → 쓰기를 한 트랜잭션에 묶으면 외부 API가 느려질 때
     * DB 커넥션을 불필요하게 오래 붙잡게 된다.
     */
    public CommuteProfileResponse update(Long memberId, Long profileId, CommuteProfileUpdateRequest request) {
        Location departure = toLocation(request.departure());
        Location arrival = toLocation(request.arrival());
        int averageDurationMinutes = calculateDuration(departure, arrival, request.transportMode());

        return transactionTemplate.execute(status -> {
            CommuteProfile profile = findOwnedOrThrow(memberId, profileId);
            profile.update(request.commuteType(), request.frequentRouteName(), departure, arrival);

            CommuteRoute route = findRouteOrThrow(profile.getId());
            route.update(request.transportMode(), averageDurationMinutes, route.getRouteDetail());

            return CommuteProfileResponse.from(profile, route);
        });
    }

    @Transactional
    public void delete(Long memberId, Long profileId) {
        CommuteProfile profile = findOwnedOrThrow(memberId, profileId);
        commuteRouteRepository.findByCommuteProfileId(profile.getId())
                .ifPresent(commuteRouteRepository::delete);
        commuteProfileRepository.delete(profile);
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

    private CommuteRoute findRouteOrThrow(Long profileId) {
        return commuteRouteRepository.findByCommuteProfileId(profileId)
                .orElseThrow(() -> new IllegalStateException("CommuteProfile에 연결된 CommuteRoute가 없습니다: " + profileId));
    }
}
