package com.techeer.backend.domain.ranking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class RankingDtos {
    private RankingDtos() {
    }

    public record Data<T>(T data) {
    }

    public record LocationInput(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
            @NotNull @DecimalMin("0") Double accuracyMeters,
            @NotNull Instant recordedAt) {
    }

    public record LocationRequest(@NotNull @Positive Long userId, @NotNull @Valid LocationInput location) {
    }

    public record StartRequest(
            @NotNull UUID sessionId,
            @NotNull @Positive Long userId,
            @NotBlank @Size(max = 128) String trackId,
            @NotNull Instant startedAt,
            @Valid LocationInput location) {
    }

    public enum EventType { TRACK_COMPLETED, TRACK_SKIPPED }

    public record EventRequest(
            @NotNull UUID eventId,
            @NotNull UUID sessionId,
            @NotNull EventType eventType,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double playedRatio,
            @NotNull Instant occurredAt) {
    }

    public record TrackInput(
            @NotBlank @Size(max = 256) String title,
            @NotBlank @Size(max = 256) String artistName,
            @Size(max = 2048) String previewUrl,
            @Size(max = 2048) String externalUrl) {
    }

    public record Track(String id, String title, String artistName, String previewUrl, String externalUrl) {
    }

    public record SessionResult(UUID sessionId, boolean created, String locationStatus) {
    }

    public record EventResult(UUID eventId, boolean created, boolean counted, String locationStatus) {
    }

    public record Item(int rank, long uniqueListenerCount, Track track) {
    }

    public record ChartResponse(String locationLabel, OffsetDateTime rankedHour, OffsetDateTime updatedAt,
                                boolean fallbackApplied, String scope, String status, List<Item> items) {
    }
}
