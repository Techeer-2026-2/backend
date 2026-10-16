package com.techeer.backend.domain.playlist.dto;

import com.techeer.backend.domain.playlist.entity.Playlist;
import com.techeer.backend.domain.playlist.entity.PlaylistTrack;
import java.util.Comparator;
import java.util.List;

public record PlaylistDetailResponse(
        Long id, String name, String description, Boolean isPublic, List<PlaylistTrackResponse> tracks) {

    public static PlaylistDetailResponse from(Playlist playlist) {
        return new PlaylistDetailResponse(
                playlist.getId(),
                playlist.getName(),
                playlist.getDescription(),
                playlist.getIsPublic(),
                playlist.getTracks().stream()
                        .sorted(Comparator.comparing(
                                PlaylistTrack::getTrackOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(PlaylistTrackResponse::from)
                        .toList());
    }
}
