package com.techeer.backend.domain.playlist.dto;

import com.techeer.backend.domain.playlist.entity.Playlist;
import com.techeer.backend.domain.playlist.entity.PlaylistType;
import java.util.List;

public record PlaylistDetailResponse(Long id, String name, PlaylistType type, List<PlaylistTrackResponse> tracks) {

    public static PlaylistDetailResponse from(Playlist playlist) {
        return new PlaylistDetailResponse(
                playlist.getId(),
                playlist.getName(),
                playlist.getType(),
                playlist.getTracks().stream().map(PlaylistTrackResponse::from).toList());
    }
}
