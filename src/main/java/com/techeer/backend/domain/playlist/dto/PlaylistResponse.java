package com.techeer.backend.domain.playlist.dto;

import com.techeer.backend.domain.playlist.entity.Playlist;

public record PlaylistResponse(Long id, String name, int trackCount) {

    public static PlaylistResponse from(Playlist playlist) {
        return new PlaylistResponse(playlist.getId(), playlist.getName(), playlist.getTracks().size());
    }
}
