package com.techeer.backend.domain.ranking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.backend.domain.ranking.dto.RankingDtos.EventRequest;
import com.techeer.backend.domain.ranking.dto.RankingDtos.EventResult;
import com.techeer.backend.domain.ranking.dto.RankingDtos.EventType;
import com.techeer.backend.domain.ranking.dto.RankingDtos.LocationInput;
import com.techeer.backend.domain.ranking.dto.RankingDtos.LocationRequest;
import com.techeer.backend.domain.ranking.dto.RankingDtos.SessionResult;
import com.techeer.backend.domain.ranking.dto.RankingDtos.StartRequest;
import com.techeer.backend.domain.ranking.dto.RankingDtos.Track;
import com.techeer.backend.domain.ranking.dto.RankingDtos.TrackInput;
import com.techeer.backend.domain.ranking.entity.RankingModels.City;
import com.techeer.backend.domain.ranking.entity.RankingModels.Session;
import com.techeer.backend.domain.ranking.repository.RankingRepository;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaybackService {
    private final RankingRepository repository;
    private final RankingGeo geo;
    private final Clock clock;
    private final ObjectMapper mapper;

    @Transactional
    public void updateLocation(long memberId, LocationRequest request) {
        validateLocation(request.location(), clock.instant());
        repository.saveLocation(memberId, request.location(), geo.cell(request.location().lat(), request.location().lng()));
    }

    @Transactional
    public SessionResult start(long memberId, StartRequest request) {
        require(!request.startedAt().isAfter(clock.instant()));
        // 회원 번호까지 포함해 해시하므로, 다른 회원이 같은 sessionId 를 쓰면 409(PLAYBACK_CONFLICT)가 된다.
        String hash = hash(memberId, request);
        var existing = repository.session(request.sessionId());
        if (existing.isPresent()) {
            sameRequest(existing.get().requestHash(), hash);
            return new SessionResult(request.sessionId(), false, existing.get().locationStatus());
        }
        track(request.trackId());
        LocationInput location = request.location() != null ? request.location()
                : repository.location(memberId, request.startedAt()).orElse(null);
        String status = "MISSING";
        String cell = null;
        City city = null;
        if (location != null) {
            validateLocation(location, request.startedAt());
            if (Duration.between(location.recordedAt(), request.startedAt()).compareTo(Duration.ofSeconds(30)) > 0) {
                status = "STALE";
            } else if (location.accuracyMeters() > 100) {
                status = "INACCURATE";
            } else {
                status = "VALID";
                cell = geo.cell(location.lat(), location.lng());
                city = repository.city(cell).orElse(null);
            }
        }
        Session session = new Session(request.sessionId(), memberId, request.trackId(), request.startedAt(),
                cell, city == null ? null : city.id(), city == null ? null : city.label(), status, hash);
        boolean created = repository.insertSession(session);
        Session saved = repository.session(request.sessionId()).orElseThrow();
        sameRequest(saved.requestHash(), hash);
        return new SessionResult(saved.id(), created, saved.locationStatus());
    }

    @Transactional
    public EventResult record(long memberId, EventRequest request) {
        require(Double.isFinite(request.playedRatio()) && request.playedRatio() >= 0 && request.playedRatio() <= 1);
        require(!request.occurredAt().isAfter(clock.instant()));
        Session session = repository.session(request.sessionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAYBACK_SESSION_NOT_FOUND));
        // 남의 세션에는 이벤트를 쓸 수 없다. 세션이 있다는 사실도 알리지 않으려고 404 로 응답한다.
        if (session.userId() != memberId) {
            throw new BusinessException(ErrorCode.PLAYBACK_SESSION_NOT_FOUND);
        }
        require(!request.occurredAt().isBefore(session.startedAt()));
        String hash = hash(request);
        boolean created = repository.insertEvent(request, hash, clock.instant());
        // 별도 eventId 로 같은 세션을 재전송해도 한 번만 완주로 집계한다.
        sameRequest(repository.eventHash(request.eventId()).orElse(""), hash);
        boolean counted = request.eventType() == EventType.TRACK_COMPLETED && request.playedRatio() >= 0.9;
        return new EventResult(request.eventId(), created, counted, session.locationStatus());
    }

    @Transactional
    public Track saveTrack(String id, TrackInput input) {
        require(id != null && !id.isBlank() && id.length() <= 128);
        validateUrl(input.previewUrl());
        validateUrl(input.externalUrl());
        repository.saveTrack(id, input);
        return track(id);
    }

    @Transactional(readOnly = true)
    public Track track(String id) {
        return repository.track(id).orElseThrow(() -> new BusinessException(ErrorCode.TRACK_NOT_FOUND));
    }

    private void validateLocation(LocationInput location, Instant reference) {
        geo.cell(location.lat(), location.lng());
        require(Double.isFinite(location.accuracyMeters()) && location.accuracyMeters() >= 0);
        require(!location.recordedAt().isAfter(reference));
    }

    private void validateUrl(String url) {
        if (url == null) {
            return;
        }
        try {
            URI uri = new URI(url);
            require("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null);
        } catch (URISyntaxException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private String hash(Object... parts) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(parts)));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("재생 요청의 멱등성 키를 만들 수 없습니다", exception);
        }
    }

    private void sameRequest(String actual, String expected) {
        if (!actual.equals(expected)) {
            throw new BusinessException(ErrorCode.PLAYBACK_CONFLICT);
        }
    }

    private void require(boolean valid) {
        if (!valid) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
