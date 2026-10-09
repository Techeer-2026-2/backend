package com.techeer.backend.domain.playlist.dto;

import com.techeer.backend.domain.playlist.entity.PlaylistTrack;

public record PlaylistTrackResponse(
        Long id,
        Long itunesTrackId,
        String trackName,
        String artistName,
        String collectionName,
        String artworkUrl,
        String previewUrl,
        Integer trackTimeMillis) {

    public static PlaylistTrackResponse from(PlaylistTrack track) {
        return new PlaylistTrackResponse(
                track.getId(),
                track.getItunesTrackId(),
                track.getTrackName(),
                track.getArtistName(),
                track.getCollectionName(),
                track.getArtworkUrl(),
                track.getPreviewUrl(),
                track.getTrackTimeMillis());
    }
}
