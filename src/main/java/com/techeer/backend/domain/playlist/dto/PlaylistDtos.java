package com.techeer.backend.domain.playlist.dto;

import com.techeer.backend.domain.ranking.dto.RankingDtos.Track;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public final class PlaylistDtos {
    private PlaylistDtos() {
    }

    public record Data<T>(T data) {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 100) String title,
            @Size(max = 500) String description,
            @NotNull @Size(max = 100) List<@NotBlank @Size(max = 128) String> trackIds) {
    }

    public record ReplaceTracksRequest(
            @NotNull @Size(max = 100) List<@NotBlank @Size(max = 128) String> trackIds) {
    }

    public record ListeningHeartbeatRequest(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
            @NotNull Instant recordedAt) {
    }

    public record PlaylistResponse(Long playlistId, String title, String description,
                                   List<Track> tracks, String shareCode, LocalDateTime createdAt) {
    }

    public record PlaylistSummary(Long playlistId, String title, int trackCount,
                                  boolean shared, LocalDateTime createdAt) {
    }

    public record ShareResponse(String shareCode, String apiPath) {
    }

    public record HeartbeatResponse(String h3Cell, Instant activeUntil) {
    }

    public record ActiveListenersResponse(Long playlistId, String h3Cell,
                                          long activeListenerCount, Instant asOf) {
    }
}
