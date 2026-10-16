package com.techeer.backend.domain.playlist.service;

import com.techeer.backend.domain.playlist.dto.ActiveListenersResponse;
import com.techeer.backend.domain.playlist.dto.HeartbeatResponse;
import com.techeer.backend.domain.playlist.dto.ListeningHeartbeatRequest;
import com.techeer.backend.domain.playlist.dto.PlaylistDetailResponse;
import com.techeer.backend.domain.playlist.dto.ShareLinkResponse;
import com.techeer.backend.domain.playlist.entity.Playlist;
import com.techeer.backend.domain.playlist.repository.ActiveListeningRepository;
import com.techeer.backend.domain.playlist.repository.PlaylistRepository;
import com.techeer.backend.domain.ranking.service.RankingGeo;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 플레이리스트 공유 링크와 "같은 셀에서 같은 플레이리스트를 듣는 인원" 집계.
 *
 * <p>청취 인원은 앱이 보낸 재생 신호(heartbeat)를 그대로 믿는 수치다. 서버가 실제 재생을 검증하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PlaylistShareService {

    /** 마지막 재생 신호가 이 시간 안이어야 청취 중으로 센다. */
    static final long HEARTBEAT_SECONDS = 60;
    /** 위치 측정 시각이 이 시간 안이어야 현재 셀에서 듣는 것으로 센다. */
    static final long LOCATION_SECONDS = 30;
    private static final long FUTURE_TOLERANCE_SECONDS = 5;
    private static final String SHARED_PATH = "/api/v1/shared/playlists/";

    private final PlaylistRepository playlistRepository;
    private final ActiveListeningRepository listeningRepository;
    private final RankingGeo geo;
    private final Clock clock;

    /**
     * 공유 토큰을 만든다. 이미 공유 중이면 같은 토큰을 돌려준다. 곡이 없는 플레이리스트는 공유할 수 없다.
     *
     * @throws BusinessException 내 플레이리스트가 아니면 PLAYLIST_NOT_FOUND, 곡이 없으면 PLAYLIST_EMPTY
     */
    @Transactional
    public ShareLinkResponse share(Long memberId, Long playlistId) {
        Playlist playlist = findOwnedForUpdate(memberId, playlistId);
        if (playlist.getTracks().isEmpty()) {
            throw new BusinessException(ErrorCode.PLAYLIST_EMPTY);
        }
        String token = playlist.share();
        return new ShareLinkResponse(token, SHARED_PATH + token);
    }

    /** 공유를 취소하고 그 플레이리스트의 청취 기록을 지운다. 공유 중이 아니어도 성공으로 처리한다. */
    @Transactional
    public void revokeShare(Long memberId, Long playlistId) {
        Playlist playlist = findOwnedForUpdate(memberId, playlistId);
        playlist.revokeShare();
        listeningRepository.deleteForPlaylist(playlistId);
    }

    /** 공유 토큰으로 플레이리스트를 조회한다. 로그인이 필요 없다. */
    @Transactional(readOnly = true)
    public PlaylistDetailResponse getShared(String shareToken) {
        return PlaylistDetailResponse.from(findShared(shareToken));
    }

    /**
     * 재생 중 신호를 받아 회원의 현재 위치 셀과 시각을 갱신한다. 회원당 한 행이라 같은 회원의 반복 신호는 한 명으로 센다.
     *
     * @throws BusinessException 위치가 올바르지 않거나, 측정 시각이 30초보다 오래됐거나 5초보다 미래이거나,
     *         이미 저장된 신호보다 오래된 신호이면 INVALID_INPUT
     */
    @Transactional
    public HeartbeatResponse heartbeat(Long memberId, String shareToken, ListeningHeartbeatRequest request) {
        Playlist playlist = findShared(shareToken);
        Instant now = clock.instant();
        String cell = geo.cell(request.lat(), request.lng());
        validateRecordedAt(request.recordedAt(), now);

        int saved = listeningRepository.upsert(memberId, playlist.getId(), cell, request.recordedAt(), now);
        if (saved == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Instant heartbeatUntil = now.plusSeconds(HEARTBEAT_SECONDS);
        Instant locationUntil = request.recordedAt().plusSeconds(LOCATION_SECONDS);
        return new HeartbeatResponse(cell, heartbeatUntil.isBefore(locationUntil) ? heartbeatUntil : locationUntil);
    }

    /** 재생을 끝낸다. 이미 끝났거나 공유가 취소됐거나 다른 플레이리스트로 옮긴 뒤여도 에러 없이 성공으로 처리한다. */
    @Transactional
    public void stop(Long memberId, String shareToken) {
        playlistRepository.findByShareToken(shareToken)
                .ifPresent(playlist -> listeningRepository.deleteByMemberAndPlaylist(memberId, playlist.getId()));
    }

    /**
     * 조회자의 현재 셀에서 지금 이 플레이리스트를 듣는 고유 회원 수를 센다. 조회자 위치도 현재 위치여야 한다.
     *
     * @throws BusinessException 위치나 측정 시각이 올바르지 않으면 INVALID_INPUT
     */
    @Transactional(readOnly = true)
    public ActiveListenersResponse activeListeners(String shareToken, double lat, double lng, Instant recordedAt) {
        Playlist playlist = findShared(shareToken);
        Instant now = clock.instant();
        String cell = geo.cell(lat, lng);
        validateRecordedAt(recordedAt, now);
        long count = listeningRepository.countActive(
                playlist.getId(), cell, now.minusSeconds(HEARTBEAT_SECONDS), now.minusSeconds(LOCATION_SECONDS));
        return new ActiveListenersResponse(playlist.getId(), cell, count, now);
    }

    /** 신호가 끊겨 만료된 청취 기록을 지운다. 집계는 만료 시각을 직접 보므로 이 정리가 늦어도 숫자는 정확하다. */
    @Transactional
    public int cleanupExpired() {
        Instant now = clock.instant();
        return listeningRepository.deleteExpired(now.minusSeconds(HEARTBEAT_SECONDS), now.minusSeconds(LOCATION_SECONDS));
    }

    private Playlist findOwnedForUpdate(Long memberId, Long playlistId) {
        Playlist playlist = playlistRepository.findByIdForUpdate(playlistId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAYLIST_NOT_FOUND));
        if (!playlist.getMemberId().equals(memberId)) {
            // 남의 플레이리스트는 있는지 없는지도 알려 주지 않는다(기존 플레이리스트 API 와 같은 정책).
            throw new BusinessException(ErrorCode.PLAYLIST_NOT_FOUND);
        }
        return playlist;
    }

    private Playlist findShared(String shareToken) {
        return playlistRepository.findByShareToken(shareToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAYLIST_NOT_FOUND));
    }

    private void validateRecordedAt(Instant recordedAt, Instant now) {
        if (recordedAt.isBefore(now.minusSeconds(LOCATION_SECONDS))
                || recordedAt.isAfter(now.plusSeconds(FUTURE_TOLERANCE_SECONDS))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
