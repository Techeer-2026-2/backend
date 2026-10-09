package com.techeer.backend.domain.playlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PlaylistTrackAddRequest(
        @NotNull Long itunesTrackId,
        @NotBlank String trackName,
        @NotBlank String artistName,
        String collectionName,
        String artworkUrl,
        String previewUrl,
        Integer trackTimeMillis) {
}
