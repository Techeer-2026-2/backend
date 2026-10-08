package com.techeer.backend.domain.playlist.dto;

public record PlaylistTrackAddRequest(
        Long itunesTrackId,
        String trackName,
        String artistName,
        String collectionName,
        String artworkUrl,
        String previewUrl,
        Integer trackTimeMillis) {
}
