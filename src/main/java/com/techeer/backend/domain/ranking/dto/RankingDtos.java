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

    public record LocationRequest(@NotNull @Valid LocationInput location) {
    }

    public record StartRequest(
            @NotNull UUID sessionId,
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

    /**
     * @param artworkUrl      앨범 커버 이미지 URL (HTTPS)
     * @param trackTimeMillis 곡 길이(밀리초). 음악 검색 응답의 trackTimeMillis 와 같은 단위
     */
    public record TrackInput(
            @NotBlank @Size(max = 256) String title,
            @NotBlank @Size(max = 256) String artistName,
            @Size(max = 2048) String previewUrl,
            @Size(max = 2048) String externalUrl,
            @Size(max = 2048) String artworkUrl,
            @Positive Integer trackTimeMillis) {

        /** 커버·곡 길이 없이 등록하는 기존 호출(데모 데이터, 테스트)용 */
        public TrackInput(String title, String artistName, String previewUrl, String externalUrl) {
            this(title, artistName, previewUrl, externalUrl, null, null);
        }
    }

    public record Track(String id, String title, String artistName, String previewUrl, String externalUrl,
                        String artworkUrl, Integer trackTimeMillis) {
    }

    public record SessionResult(UUID sessionId, boolean created, String locationStatus) {
    }

    public record EventResult(UUID eventId, boolean created, boolean counted, String locationStatus) {
    }

    /**
     * @param rankChange 직전 한 시간 차트의 순위에서 현재 순위를 뺀 값(양수 상승, 음수 하락, 0 유지).
     *        직전 시간에 없던 곡이면 null 이라 응답에서 빠진다
     */
    public record Item(int rank, long uniqueListenerCount, Integer rankChange, Track track) {
    }

    public record ChartResponse(String locationLabel, OffsetDateTime rankedHour, OffsetDateTime updatedAt,
                                boolean fallbackApplied, String scope, String status, List<Item> items) {
    }
}
