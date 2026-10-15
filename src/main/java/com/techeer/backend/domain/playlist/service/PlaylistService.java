package com.techeer.backend.domain.playlist.service;

import com.techeer.backend.domain.account.service.AuthService;
import com.techeer.backend.domain.playlist.dto.PlaylistDtos.ActiveListenersResponse;
import com.techeer.backend.domain.playlist.dto.PlaylistDtos.CreateRequest;
import com.techeer.backend.domain.playlist.dto.PlaylistDtos.HeartbeatResponse;
import com.techeer.backend.domain.playlist.dto.PlaylistDtos.ListeningHeartbeatRequest;
import com.techeer.backend.domain.playlist.dto.PlaylistDtos.PlaylistResponse;
import com.techeer.backend.domain.playlist.dto.PlaylistDtos.PlaylistSummary;
import com.techeer.backend.domain.playlist.dto.PlaylistDtos.ShareResponse;
import com.techeer.backend.domain.playlist.entity.ActiveListening;
import com.techeer.backend.domain.playlist.entity.MusicPlaylist;
import com.techeer.backend.domain.playlist.repository.ActiveListeningRepository;
import com.techeer.backend.domain.playlist.repository.MusicPlaylistRepository;
import com.techeer.backend.domain.ranking.dto.RankingDtos.Track;
import com.techeer.backend.domain.ranking.repository.RankingRepository;
import com.techeer.backend.domain.ranking.service.RankingGeo;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaylistService {
    private static final long HEARTBEAT_SECONDS = 60;
    private static final long LOCATION_SECONDS = 30;

    private final AuthService auth;
    private final MusicPlaylistRepository playlists;
    private final ActiveListeningRepository listeners;
    private final RankingRepository tracks;
    private final RankingGeo geo;
    private final Clock clock;

    @Transactional
    public PlaylistResponse create(String subject, CreateRequest request) {
        Long userId = auth.currentUser(subject).userId();
        validateTracks(request.trackIds());
        MusicPlaylist playlist = playlists.save(new MusicPlaylist(userId, request.title().strip(),
                request.description() == null ? null : request.description().strip(), request.trackIds()));
        return detail(playlist);
    }

    @Transactional(readOnly = true)
    public List<PlaylistSummary> mine(String subject) {
        Long userId = auth.currentUser(subject).userId();
        return playlists.findByOwnerUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId).stream()
                .map(p -> new PlaylistSummary(p.getPlaylistId(), p.getTitle(), p.getTrackIds().size(),
                        p.getShareCode() != null, p.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlaylistResponse mine(String subject, Long playlistId) {
        return detail(owned(subject, playlistId));
    }

    @Transactional
    public PlaylistResponse replaceTracks(String subject, Long playlistId, List<String> trackIds) {
        MusicPlaylist playlist = owned(subject, playlistId);
        if (playlist.getShareCode() != null && trackIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PLAYLIST_EMPTY);
        }
        validateTracks(trackIds);
        // 순서 교체 시 기존 행을 먼저 지워 DB의 (playlist_id, track_id) UNIQUE와 충돌하지 않게 한다.
        playlist.replaceTracks(List.of());
        playlists.flush();
        playlist.replaceTracks(trackIds);
        return detail(playlist);
    }

    @Transactional
    public ShareResponse share(String subject, Long playlistId) {
        MusicPlaylist playlist = owned(subject, playlistId);
        if (playlist.getTrackIds().isEmpty()) {
            throw new BusinessException(ErrorCode.PLAYLIST_EMPTY);
        }
        String code = playlist.share();
        return new ShareResponse(code, "/api/v1/shared/playlists/" + code);
    }

    @Transactional
    public void revokeShare(String subject, Long playlistId) {
        MusicPlaylist playlist = owned(subject, playlistId);
        playlist.revokeShare();
        listeners.deleteForPlaylist(playlistId);
    }

    @Transactional(readOnly = true)
    public PlaylistResponse shared(String shareCode) {
        return detail(sharedPlaylist(shareCode));
    }

    @Transactional
    public HeartbeatResponse heartbeat(String subject, String shareCode, ListeningHeartbeatRequest request) {
        Long userId = auth.currentUser(subject).userId();
        MusicPlaylist playlist = sharedPlaylist(shareCode);
        Instant now = clock.instant();
        validateLocation(request.lat(), request.lng(), request.recordedAt(), now);
        String cell = geo.cell(request.lat(), request.lng());
        ActiveListening active = listeners.findById(userId).orElse(null);
        if (active == null) {
            listeners.save(new ActiveListening(userId, playlist.getPlaylistId(), cell, request.recordedAt(), now));
        } else {
            // 지연 도착한 위치 요청이 더 최신 셀을 이전 셀로 되돌리지 않게 한다.
            if (request.recordedAt().isBefore(active.getLocationRecordedAt())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            active.update(playlist.getPlaylistId(), cell, request.recordedAt(), now);
        }
        Instant activeUntil = now.plusSeconds(HEARTBEAT_SECONDS)
                .isBefore(request.recordedAt().plusSeconds(LOCATION_SECONDS))
                ? now.plusSeconds(HEARTBEAT_SECONDS) : request.recordedAt().plusSeconds(LOCATION_SECONDS);
        return new HeartbeatResponse(cell, activeUntil);
    }

    @Transactional
    public void stop(String subject, String shareCode) {
        Long userId = auth.currentUser(subject).userId();
        Long playlistId = sharedPlaylist(shareCode).getPlaylistId();
        listeners.findById(userId).filter(active -> active.getPlaylistId().equals(playlistId))
                .ifPresent(listeners::delete);
    }

    @Transactional(readOnly = true)
    public ActiveListenersResponse activeListeners(String subject, String shareCode,
                                                    double lat, double lng, Instant recordedAt) {
        auth.currentUser(subject);
        MusicPlaylist playlist = sharedPlaylist(shareCode);
        Instant now = clock.instant();
        validateLocation(lat, lng, recordedAt, now);
        String cell = geo.cell(lat, lng);
        long count = listeners.countActive(playlist.getPlaylistId(), cell,
                now.minusSeconds(HEARTBEAT_SECONDS), now.minusSeconds(LOCATION_SECONDS));
        return new ActiveListenersResponse(playlist.getPlaylistId(), cell, count, now);
    }

    @Transactional
    public int cleanupExpired() {
        Instant now = clock.instant();
        return listeners.deleteExpired(now.minusSeconds(HEARTBEAT_SECONDS),
                now.minusSeconds(LOCATION_SECONDS));
    }

    private MusicPlaylist owned(String subject, Long playlistId) {
        Long userId = auth.currentUser(subject).userId();
        MusicPlaylist playlist = playlists.findByPlaylistIdAndDeletedAtIsNull(playlistId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAYLIST_NOT_FOUND));
        if (!playlist.getOwnerUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PLAYLIST_FORBIDDEN);
        }
        return playlist;
    }

    private MusicPlaylist sharedPlaylist(String shareCode) {
        return playlists.findByShareCodeAndDeletedAtIsNull(shareCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAYLIST_NOT_FOUND));
    }

    private void validateTracks(List<String> trackIds) {
        if (new HashSet<>(trackIds).size() != trackIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        trackIds.forEach(id -> tracks.track(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRACK_NOT_FOUND)));
    }

    private void validateLocation(double lat, double lng, Instant recordedAt, Instant now) {
        geo.cell(lat, lng);
        if (recordedAt.isBefore(now.minusSeconds(LOCATION_SECONDS)) || recordedAt.isAfter(now.plusSeconds(5))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private PlaylistResponse detail(MusicPlaylist playlist) {
        List<Track> trackDetails = playlist.getTrackIds().stream()
                .map(id -> tracks.track(id).orElseThrow(() -> new BusinessException(ErrorCode.TRACK_NOT_FOUND)))
                .toList();
        return new PlaylistResponse(playlist.getPlaylistId(), playlist.getTitle(), playlist.getDescription(),
                trackDetails, playlist.getShareCode(), playlist.getCreatedAt());
    }
}
