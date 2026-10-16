package com.techeer.backend.domain.commutesession.service;

import com.techeer.backend.domain.commuteprofile.entity.CommuteProfile;
import com.techeer.backend.domain.commuteprofile.entity.CommuteRoute;
import com.techeer.backend.domain.commuteprofile.repository.CommuteProfileRepository;
import com.techeer.backend.domain.commuteprofile.repository.CommuteRouteRepository;
import com.techeer.backend.domain.commutesession.dto.CommuteSessionResponse;
import com.techeer.backend.domain.commutesession.dto.CommuteSessionStartRequest;
import com.techeer.backend.domain.commutesession.dto.CommuteSessionSummaryResponse;
import com.techeer.backend.domain.commutesession.dto.CommuteSessionSummaryResponse.TrackSummary;
import com.techeer.backend.domain.commutesession.dto.PlaybackEventRequest;
import com.techeer.backend.domain.commutesession.dto.PlaybackEventResponse;
import com.techeer.backend.domain.commutesession.entity.CommuteSession;
import com.techeer.backend.domain.commutesession.entity.CommuteSessionEvent;
import com.techeer.backend.domain.commutesession.entity.CommuteSessionStatus;
import com.techeer.backend.domain.commutesession.entity.PlaybackAction;
import com.techeer.backend.domain.commutesession.repository.CommuteSessionEventRepository;
import com.techeer.backend.domain.commutesession.repository.CommuteSessionRepository;
import com.techeer.backend.domain.commutesession.repository.EventTotals;
import com.techeer.backend.domain.playlist.entity.Track;
import com.techeer.backend.domain.playlist.repository.PlaylistRepository;
import com.techeer.backend.domain.playlist.repository.TrackRepository;
import com.techeer.backend.global.config.ClockConfig;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통근 세션: 통근 한 번의 시작·재생 이벤트·종료·취소·요약.
 *
 * <p>회원당 진행 중인 통근은 하나뿐이고, 이는 DB 제약({@code active_member_id} unique)이 최종으로 보장한다.
 * 종료·취소는 "진행 중일 때만" 바꾸는 조건부 UPDATE 한 문장이라 동시에 와도 한쪽만 성공한다.
 */
@Service
@RequiredArgsConstructor
public class CommuteSessionService {

    private final CommuteSessionRepository sessionRepository;
    private final CommuteSessionEventRepository eventRepository;
    private final CommuteProfileRepository commuteProfileRepository;
    private final CommuteRouteRepository commuteRouteRepository;
    private final PlaylistRepository playlistRepository;
    private final TrackRepository trackRepository;
    private final Clock clock;

    /**
     * 통근을 시작한다. 예상 소요시간은 통근 프로필 경로의 평균 소요시간을 그대로 가져온다(경로가 없으면 null).
     *
     * @throws BusinessException 내 통근 프로필·플레이리스트가 아니면 *_NOT_FOUND, 이미 진행 중인 통근이 있으면 409
     */
    @Transactional
    public CommuteSessionResponse start(Long memberId, CommuteSessionStartRequest request) {
        CommuteProfile profile = commuteProfileRepository.findById(request.commuteProfileId())
                .filter(p -> p.getMemberId().equals(memberId))   // 남의 프로필은 존재 자체를 숨긴다
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMUTE_PROFILE_NOT_FOUND));
        if (request.playlistId() != null) {
            playlistRepository.findById(request.playlistId())
                    .filter(p -> p.getMemberId().equals(memberId))
                    .orElseThrow(() -> new BusinessException(ErrorCode.PLAYLIST_NOT_FOUND));
        }
        if (sessionRepository.findByMemberIdAndStatus(memberId, CommuteSessionStatus.ACTIVE).isPresent()) {
            throw new BusinessException(ErrorCode.COMMUTE_SESSION_ALREADY_ACTIVE);
        }

        Integer expectedMinutes = commuteRouteRepository.findByCommuteProfileId(profile.getId())
                .map(CommuteRoute::getAverageDurationMinutes)
                .filter(minutes -> minutes > 0)
                .orElse(null);
        CommuteSession session = CommuteSession.start(
                memberId, profile.getId(), request.playlistId(), LocalDateTime.now(clock), expectedMinutes);
        try {
            // save 가 아니라 saveAndFlush: 위의 확인을 동시에 통과한 요청은 DB 의 unique 제약이 여기서 막는다.
            return CommuteSessionResponse.from(sessionRepository.saveAndFlush(session));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.COMMUTE_SESSION_ALREADY_ACTIVE);
        }
    }

    /** 지금 진행 중인 통근. 없으면 404 이다(앱이 다시 접속했을 때 복구하는 용도). */
    @Transactional(readOnly = true)
    public CommuteSessionResponse getActive(Long memberId) {
        return sessionRepository.findByMemberIdAndStatus(memberId, CommuteSessionStatus.ACTIVE)
                .map(CommuteSessionResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMUTE_SESSION_NOT_FOUND));
    }

    /**
     * 재생 이벤트를 기록한다. 같은 eventId 를 같은 내용으로 다시 보내면 새로 쓰지 않고 created=false 로 돌려준다.
     * 같은 eventId 에 내용이 다르거나 다른 통근의 이벤트이면 409 이다.
     *
     * @throws BusinessException 내 통근이 아니면 NOT_FOUND, 끝난 통근에 새 이벤트를 보내면 NOT_ACTIVE
     */
    @Transactional
    public PlaybackEventResponse recordEvent(Long memberId, Long sessionId, PlaybackEventRequest request) {
        CommuteSession session = findOwned(memberId, sessionId);
        if (eventRepository.findByEventId(request.eventId()).isPresent()) {
            return replay(session, request);
        }
        if (!session.isActive()) {
            throw new BusinessException(ErrorCode.COMMUTE_SESSION_NOT_ACTIVE);
        }
        int inserted = eventRepository.insertIfAbsent(request.eventId(), sessionId, request.itunesTrackId(),
                request.action().name(), request.playedSeconds(), LocalDateTime.now(clock));
        if (inserted == 0) {
            // 위 확인과 넣기 사이에 같은 이벤트가 먼저 들어간 경우
            return replay(session, request);
        }
        return new PlaybackEventResponse(request.eventId(), true);
    }

    /** 통근을 종료하고 결과 요약을 돌려준다. */
    @Transactional
    public CommuteSessionSummaryResponse complete(Long memberId, Long sessionId) {
        finish(memberId, sessionId, CommuteSessionStatus.COMPLETED);
        return summary(memberId, sessionId);
    }

    /** 통근을 취소한다. */
    @Transactional
    public CommuteSessionResponse cancel(Long memberId, Long sessionId) {
        finish(memberId, sessionId, CommuteSessionStatus.CANCELED);
        return CommuteSessionResponse.from(findOwned(memberId, sessionId));
    }

    /** 곡·시간 요약. 진행 중이면 지금까지의 값, 끝났으면 최종 값이다. */
    @Transactional(readOnly = true)
    public CommuteSessionSummaryResponse summary(Long memberId, Long sessionId) {
        CommuteSession session = findOwned(memberId, sessionId);
        LocalDateTime end = session.getEndedAt() != null ? session.getEndedAt() : LocalDateTime.now(clock);
        long commuteSeconds = Math.max(0, Duration.between(session.getStartedAt(), end).toSeconds());

        Map<Long, int[]> counts = new HashMap<>();   // 곡 → [재생, 건너뜀, 다시 들음]
        Map<Long, Long> seconds = new HashMap<>();
        for (EventTotals totals : eventRepository.totalsBySession(sessionId)) {
            int[] c = counts.computeIfAbsent(totals.itunesTrackId(), id -> new int[3]);
            c[totals.action().ordinal()] += (int) totals.count();
            seconds.merge(totals.itunesTrackId(), totals.playedSeconds(), Long::sum);
        }
        Map<Long, Track> trackInfo = trackRepository.findAllByItunesTrackIdIn(counts.keySet()).stream()
                .collect(Collectors.toMap(Track::getItunesTrackId, Function.identity(), (a, b) -> a));

        List<TrackSummary> tracks = counts.keySet().stream()
                .map(id -> trackSummary(id, counts.get(id), seconds.getOrDefault(id, 0L), trackInfo.get(id)))
                .sorted(Comparator.comparingLong(TrackSummary::playedSeconds).reversed()
                        .thenComparing(TrackSummary::itunesTrackId))
                .toList();
        return new CommuteSessionSummaryResponse(
                session.getId(),
                session.getStatus(),
                session.getStartedAt().atZone(ClockConfig.SEOUL).toOffsetDateTime(),
                session.getEndedAt() == null ? null : session.getEndedAt().atZone(ClockConfig.SEOUL).toOffsetDateTime(),
                session.getExpectedDurationMinutes(),
                commuteSeconds,
                (int) tracks.stream().filter(t -> t.playCount() > 0).count(),
                tracks.stream().mapToLong(TrackSummary::playedSeconds).sum(),
                tracks.stream().mapToInt(TrackSummary::skipCount).sum(),
                tracks);
    }

    private static TrackSummary trackSummary(Long itunesTrackId, int[] counts, long playedSeconds, Track track) {
        return new TrackSummary(
                itunesTrackId,
                track == null ? null : track.getTitle(),
                track == null ? null : track.getArtist(),
                track == null ? null : track.getArtworkUrl(),
                counts[PlaybackAction.PLAY.ordinal()],
                counts[PlaybackAction.SKIP.ordinal()],
                counts[PlaybackAction.REPEAT.ordinal()],
                playedSeconds);
    }

    /** 진행 중인 통근을 끝낸다. 내 통근이 아니면 NOT_FOUND, 이미 끝났으면 NOT_ACTIVE. */
    private void finish(Long memberId, Long sessionId, CommuteSessionStatus status) {
        int changed = sessionRepository.finish(sessionId, memberId, status, LocalDateTime.now(clock));
        if (changed == 0) {
            findOwned(memberId, sessionId);
            throw new BusinessException(ErrorCode.COMMUTE_SESSION_NOT_ACTIVE);
        }
    }

    private CommuteSession findOwned(Long memberId, Long sessionId) {
        return sessionRepository.findByIdAndMemberId(sessionId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMUTE_SESSION_NOT_FOUND));
    }

    /** 이미 있는 이벤트와 내용이 같으면 중복 요청으로 보고 성공, 다르면 409. */
    private PlaybackEventResponse replay(CommuteSession session, PlaybackEventRequest request) {
        CommuteSessionEvent existing = eventRepository.findByEventId(request.eventId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMUTE_SESSION_EVENT_CONFLICT));
        boolean same = existing.getSessionId().equals(session.getId())
                && existing.getItunesTrackId().equals(request.itunesTrackId())
                && existing.getAction() == request.action()
                && Objects.equals(existing.getPlayedSeconds(), request.playedSeconds());
        if (!same) {
            throw new BusinessException(ErrorCode.COMMUTE_SESSION_EVENT_CONFLICT);
        }
        return new PlaybackEventResponse(request.eventId(), false);
    }
}
