package com.techeer.backend.domain.playlist.dto;

import com.techeer.backend.domain.playlist.entity.PlaylistTrack;
import com.techeer.backend.domain.playlist.entity.Track;

public record PlaylistTrackResponse(
        Long id,
        Long itunesTrackId,
        String trackName,
        String artistName,
        String collectionName,
        String artworkUrl,
        String previewUrl,
        Integer trackTimeMillis,
        String genre) {

    public static PlaylistTrackResponse from(PlaylistTrack playlistTrack) {
        Track track = playlistTrack.getTrack();
        return new PlaylistTrackResponse(
                playlistTrack.getId(),
                track.getItunesTrackId(),
                track.getTitle(),
                track.getArtist(),
                track.getCollectionName(),
                track.getArtworkUrl(),
                track.getPreviewUrl(),
                track.getTrackTimeMillis(),
                track.getGenre());
    }
}
