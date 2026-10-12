package com.techeer.backend.domain.playlist.service;

import com.techeer.backend.domain.playlist.dto.PlaylistCreateRequest;
import com.techeer.backend.domain.playlist.dto.PlaylistDetailResponse;
import com.techeer.backend.domain.playlist.dto.PlaylistResponse;
import com.techeer.backend.domain.playlist.dto.PlaylistTrackAddRequest;
import com.techeer.backend.domain.playlist.entity.Playlist;
import com.techeer.backend.domain.playlist.entity.PlaylistTrack;
import com.techeer.backend.domain.playlist.repository.PlaylistRepository;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaylistService {

    private final PlaylistRepository playlistRepository;

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
        requests.forEach(request -> playlist.getTracks().add(
                PlaylistTrack.builder()
                        .playlist(playlist)
                        .itunesTrackId(request.itunesTrackId())
                        .trackName(request.trackName())
                        .artistName(request.artistName())
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
