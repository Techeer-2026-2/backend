package com.techeer.backend.domain.playlist.service;

import com.techeer.backend.domain.playlist.dto.PlaylistCreateRequest;
import com.techeer.backend.domain.playlist.dto.PlaylistDetailResponse;
import com.techeer.backend.domain.playlist.dto.PlaylistResponse;
import com.techeer.backend.domain.playlist.dto.PlaylistTrackAddRequest;
import com.techeer.backend.domain.playlist.entity.Playlist;
import com.techeer.backend.domain.playlist.entity.PlaylistTrack;
import com.techeer.backend.domain.playlist.entity.Track;
import com.techeer.backend.domain.playlist.repository.PlaylistRepository;
import com.techeer.backend.domain.playlist.repository.TrackRepository;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final TrackRepository trackRepository;
    private final PlatformTransactionManager transactionManager;

    @Transactional
    public PlaylistResponse create(Long memberId, PlaylistCreateRequest request) {
        Playlist playlist = Playlist.builder()
                .memberId(memberId)
                .name(request.name())
                .build();
        return PlaylistResponse.from(playlistRepository.save(playlist));
    }

    public List<PlaylistResponse> findAll(Long memberId) {
        return playlistRepository.findAllByMemberId(memberId).stream()
                .map(PlaylistResponse::from)
                .toList();
    }

    public PlaylistDetailResponse findDetail(Long memberId, Long playlistId) {
        return PlaylistDetailResponse.from(findOwnedOrThrow(memberId, playlistId));
    }

    @Transactional
    public void delete(Long memberId, Long playlistId) {
        playlistRepository.delete(findOwnedOrThrow(memberId, playlistId));
    }

    @Transactional
    public void addTracks(Long memberId, Long playlistId, List<PlaylistTrackAddRequest> requests) {
        Playlist playlist = findOwnedOrThrow(memberId, playlistId);
        requests.forEach(request -> {
            Track track = findOrCreateTrack(request);
            playlist.getTracks().add(PlaylistTrack.builder()
                    .playlist(playlist)
                    .track(track)
                    .build());
        });
    }

    private Track findOrCreateTrack(PlaylistTrackAddRequest request) {
        return trackRepository.findByItunesTrackId(request.itunesTrackId())
                .orElseGet(() -> createTrackSafely(request));
    }

    private Track createTrackSafely(PlaylistTrackAddRequest request) {
        try {
            return createTrackInNewTransaction(request);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 곡이 먼저 저장된 경우: 새 트랜잭션에서만 실패했으므로
            // 바깥 트랜잭션은 멀쩡하다 → 그대로 재조회해서 재사용한다.
            return trackRepository.findByItunesTrackId(request.itunesTrackId())
                    .orElseThrow(() -> e);
        }
    }

    private Track createTrackInNewTransaction(PlaylistTrackAddRequest request) {
        TransactionTemplate newTransaction = new TransactionTemplate(transactionManager);
        newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return newTransaction.execute(status -> trackRepository.save(Track.builder()
                .itunesTrackId(request.itunesTrackId())
                .title(request.trackName())
                .artist(request.artistName())
                .genre(request.genre())
                .collectionName(request.collectionName())
                .artworkUrl(request.artworkUrl())
                .previewUrl(request.previewUrl())
                .trackTimeMillis(request.trackTimeMillis())
                .build()));
    }

    @Transactional
    public void removeTrack(Long memberId, Long playlistId, Long trackId) {
        Playlist playlist = findOwnedOrThrow(memberId, playlistId);
        boolean removed = playlist.getTracks().removeIf(track -> track.getId().equals(trackId));
        if (!removed) {
            throw new BusinessException(ErrorCode.PLAYLIST_TRACK_NOT_FOUND);
        }
    }

    private Playlist findOwnedOrThrow(Long memberId, Long playlistId) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAYLIST_NOT_FOUND));
        if (!playlist.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.PLAYLIST_NOT_FOUND);
        }
        return playlist;
    }
}
