package com.techeer.backend.domain.ranking.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RankingModels {
    private RankingModels() {
    }

    public record City(String id, String label) {
    }

    public record Session(UUID id, long userId, String trackId, Instant startedAt, String h3Cell,
                          String cityId, String cityLabel, String locationStatus, String requestHash) {
    }

    public record Completion(UUID eventId, long userId, String trackId, Instant occurredAt,
                             String h3Cell, String cityId, String cityLabel) {
    }

    public record Score(String trackId, long listeners, long completions, Instant lastCompletedAt) {
    }

    public record Chart(String key, String label, long listeners, List<Score> scores) {
    }

    public record Batch(Instant hour, Instant updatedAt) {
    }
}
