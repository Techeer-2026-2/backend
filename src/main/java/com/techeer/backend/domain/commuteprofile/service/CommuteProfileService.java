package com.techeer.backend.domain.commuteprofile.service;

import com.techeer.backend.domain.commuteprofile.dto.CommuteProfileCreateRequest;
import com.techeer.backend.domain.commuteprofile.dto.CommuteProfileResponse;
import com.techeer.backend.domain.commuteprofile.dto.CommuteProfileUpdateRequest;
import com.techeer.backend.domain.commuteprofile.dto.LocationDto;
import com.techeer.backend.domain.commuteprofile.entity.CommuteProfile;
import com.techeer.backend.domain.commuteprofile.entity.CommuteRoute;
import com.techeer.backend.domain.commuteprofile.entity.CommuteType;
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
     * PATCH라 요청에 없는(null) 필드는 기존 값을 그대로 쓴다. departure/arrival/transportMode 중
     * 하나라도 요청에 왔을 때만 TMAP을 다시 호출해 평균 소요시간을 갱신하고, 그 외(이름 등만 바뀐 경우)엔
     * 기존 소요시간을 그대로 유지해 불필요한 외부 API 호출을 막는다.
     *
     * <p>TMAP 호출(느릴 수 있음)은 DB 트랜잭션 밖에서 끝내고, 실제 쓰기(소유권 재확인 + 저장)만
     * 짧은 트랜잭션으로 묶는다. 읽기 → 외부 호출 → 쓰기를 한 트랜잭션에 묶으면 외부 API가 느려질 때
     * DB 커넥션을 불필요하게 오래 붙잡게 된다.
     */
    public CommuteProfileResponse update(Long memberId, Long profileId, CommuteProfileUpdateRequest request) {
        CommuteProfile currentProfile = findOwnedOrThrow(memberId, profileId);
        CommuteRoute currentRoute = findRouteOrThrow(currentProfile.getId());

        Location departure = request.departure() != null ? toLocation(request.departure()) : currentProfile.getDeparture();
        Location arrival = request.arrival() != null ? toLocation(request.arrival()) : currentProfile.getArrival();
        TransportMode transportMode = request.transportMode() != null ? request.transportMode() : currentRoute.getTransportMode();

        boolean routeChanged = request.departure() != null || request.arrival() != null || request.transportMode() != null;
        Integer averageDurationMinutes = routeChanged
                ? calculateDuration(departure, arrival, transportMode)
                : currentRoute.getAverageDurationMinutes();

        CommuteType commuteType = request.commuteType() != null ? request.commuteType() : currentProfile.getCommuteType();
        String frequentRouteName = request.frequentRouteName() != null
                ? request.frequentRouteName() : currentProfile.getFrequentRouteName();

        return transactionTemplate.execute(status -> {
            CommuteProfile profile = findOwnedOrThrow(memberId, profileId);
            profile.update(commuteType, frequentRouteName, departure, arrival);

            CommuteRoute route = findRouteOrThrow(profile.getId());
            route.update(transportMode, averageDurationMinutes, route.getRouteDetail());

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
