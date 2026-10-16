package com.techeer.backend.domain.playlist.dto;

import com.techeer.backend.domain.playlist.entity.Playlist;
import com.techeer.backend.domain.playlist.entity.PlaylistType;

public record PlaylistResponse(Long id, String name, PlaylistType type, int trackCount) {

    public static PlaylistResponse from(Playlist playlist) {
        return new PlaylistResponse(playlist.getId(), playlist.getName(), playlist.getType(), playlist.getTracks().size());
    }
}
