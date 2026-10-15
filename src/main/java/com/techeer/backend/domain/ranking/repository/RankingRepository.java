package com.techeer.backend.domain.ranking.repository;

import com.techeer.backend.domain.ranking.dto.RankingDtos.EventRequest;
import com.techeer.backend.domain.ranking.dto.RankingDtos.Item;
import com.techeer.backend.domain.ranking.dto.RankingDtos.LocationInput;
import com.techeer.backend.domain.ranking.dto.RankingDtos.Track;
import com.techeer.backend.domain.ranking.dto.RankingDtos.TrackInput;
import com.techeer.backend.domain.ranking.entity.RankingModels.Batch;
import com.techeer.backend.domain.ranking.entity.RankingModels.Chart;
import com.techeer.backend.domain.ranking.entity.RankingModels.City;
import com.techeer.backend.domain.ranking.entity.RankingModels.Completion;
import com.techeer.backend.domain.ranking.entity.RankingModels.Score;
import com.techeer.backend.domain.ranking.entity.RankingModels.Session;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL 제약과 upsert로 동시 전송에서도 중복 저장을 막는다. */
@Repository
@RequiredArgsConstructor
public class RankingRepository {
    private final JdbcTemplate jdbc;

    public void saveTrack(String id, TrackInput track) {
        jdbc.update("""
                INSERT INTO ranking.track(track_id, title, artist_name, preview_url, external_url) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (track_id) DO UPDATE SET title = excluded.title, artist_name = excluded.artist_name,
                preview_url = excluded.preview_url, external_url = excluded.external_url
                """, id, track.title(), track.artistName(), track.previewUrl(), track.externalUrl());
    }

    public Optional<Track> track(String id) {
        return jdbc.query("SELECT * FROM ranking.track WHERE track_id = ?", (rs, row) ->
                new Track(rs.getString("track_id"), rs.getString("title"), rs.getString("artist_name"),
                        rs.getString("preview_url"), rs.getString("external_url")), id).stream().findFirst();
    }

    public Optional<City> city(String cell) {
        return jdbc.query("SELECT city_id, city_label FROM ranking.city_cell WHERE h3_cell = ?", (rs, row) ->
                new City(rs.getString(1), rs.getString(2)), cell).stream().findFirst();
    }

    public void saveLocation(long userId, LocationInput location, String cell) {
        jdbc.update("""
                INSERT INTO ranking.latest_user_location(user_id, lat, lng, accuracy_meters, recorded_at, h3_cell)
                VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (user_id) DO UPDATE
                SET lat = excluded.lat, lng = excluded.lng, accuracy_meters = excluded.accuracy_meters,
                    recorded_at = excluded.recorded_at, h3_cell = excluded.h3_cell
                WHERE excluded.recorded_at > ranking.latest_user_location.recorded_at
                """, userId, location.lat(), location.lng(), location.accuracyMeters(), ts(location.recordedAt()), cell);
    }

    public Optional<LocationInput> location(long userId, Instant startedAt) {
        return jdbc.query("""
                SELECT * FROM ranking.latest_user_location WHERE user_id = ? AND recorded_at <= ?
                """, (rs, row) -> new LocationInput(rs.getDouble("lat"), rs.getDouble("lng"),
                rs.getDouble("accuracy_meters"), rs.getTimestamp("recorded_at").toInstant()), userId, ts(startedAt))
                .stream().findFirst();
    }

    public boolean insertSession(Session session) {
        return jdbc.update("""
                INSERT INTO ranking.playback_session(session_id, user_id, track_id, started_at, h3_cell,
                    city_id, city_label, location_status, request_hash) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (session_id) DO NOTHING
                """, session.id(), session.userId(), session.trackId(), ts(session.startedAt()), session.h3Cell(),
                session.cityId(), session.cityLabel(), session.locationStatus(), session.requestHash()) == 1;
    }

    public Optional<Session> session(UUID id) {
        return jdbc.query("SELECT * FROM ranking.playback_session WHERE session_id = ?", (rs, row) ->
                new Session(rs.getObject("session_id", UUID.class), rs.getLong("user_id"), rs.getString("track_id"),
                        rs.getTimestamp("started_at").toInstant(), rs.getString("h3_cell"), rs.getString("city_id"),
                        rs.getString("city_label"), rs.getString("location_status"), rs.getString("request_hash")), id)
                .stream().findFirst();
    }

    public boolean insertEvent(EventRequest request, String hash, Instant receivedAt) {
        return jdbc.update("""
                INSERT INTO ranking.playback_event(event_id, session_id, event_type, played_ratio, occurred_at,
                    received_at, request_hash) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, request.eventId(), request.sessionId(), request.eventType().name(), request.playedRatio(),
                ts(request.occurredAt()), ts(receivedAt), hash) == 1;
    }

    public Optional<String> eventHash(UUID id) {
        return jdbc.query("SELECT request_hash FROM ranking.playback_event WHERE event_id = ?",
                (rs, row) -> rs.getString(1), id).stream().findFirst();
    }

    public List<Completion> completions(Instant hour) {
        return jdbc.query("""
                SELECT e.event_id, s.user_id, s.track_id, e.occurred_at, s.h3_cell, s.city_id, s.city_label
                FROM ranking.playback_event e JOIN ranking.playback_session s ON s.session_id = e.session_id
                WHERE e.occurred_at >= ? AND e.occurred_at < ?
                    AND e.event_type = 'TRACK_COMPLETED' AND e.played_ratio >= 0.9
                """, (rs, row) -> new Completion(rs.getObject("event_id", UUID.class), rs.getLong("user_id"),
                rs.getString("track_id"), rs.getTimestamp("occurred_at").toInstant(), rs.getString("h3_cell"),
                rs.getString("city_id"), rs.getString("city_label")), ts(hour), ts(hour.plusSeconds(3600)));
    }

    public boolean lockHour(Instant hour) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(763142, ?)",
                Boolean.class, Math.toIntExact(hour.getEpochSecond() / 3600)));
    }

    public void replaceCharts(Instant hour, Instant updatedAt, int eventCount, long durationMs, List<Chart> charts) {
        jdbc.update("""
                INSERT INTO ranking.batch(ranked_hour, updated_at, event_count, duration_ms) VALUES (?, ?, ?, ?)
                ON CONFLICT (ranked_hour) DO UPDATE SET updated_at = excluded.updated_at,
                    event_count = excluded.event_count, duration_ms = excluded.duration_ms
                """, ts(hour), ts(updatedAt), eventCount, durationMs);
        jdbc.update("DELETE FROM ranking.hourly_chart WHERE ranked_hour = ?", ts(hour));
        List<Object[]> chartRows = new ArrayList<>();
        List<Object[]> scoreRows = new ArrayList<>();
        for (Chart chart : charts) {
            chartRows.add(new Object[]{ts(hour), chart.key(), chart.label(), chart.listeners()});
            for (int i = 0; i < chart.scores().size(); i++) {
                Score score = chart.scores().get(i);
                scoreRows.add(new Object[]{ts(hour), chart.key(), score.trackId(), i + 1, score.listeners(),
                        score.completions(), ts(score.lastCompletedAt())});
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO ranking.hourly_chart(ranked_hour, scope_key, location_label, listener_count) VALUES (?, ?, ?, ?)
                """, chartRows);
        jdbc.batchUpdate("""
                INSERT INTO ranking.hourly_track_rank(ranked_hour, scope_key, track_id, rank,
                    unique_listener_count, completion_count, last_completed_at) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, scoreRows);
    }

    public Optional<Batch> latestBatch() {
        return jdbc.query("SELECT ranked_hour, updated_at FROM ranking.batch ORDER BY ranked_hour DESC LIMIT 1",
                (rs, row) -> new Batch(rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant()))
                .stream().findFirst();
    }

    public Optional<String> eligibleLabel(Instant hour, String key) {
        return jdbc.query("""
                SELECT location_label FROM ranking.hourly_chart c WHERE ranked_hour = ? AND scope_key = ?
                    AND listener_count >= 20 AND EXISTS (SELECT 1 FROM ranking.hourly_track_rank r
                        WHERE r.ranked_hour = c.ranked_hour AND r.scope_key = c.scope_key)
                """, (rs, row) -> rs.getString(1), ts(hour), key).stream().findFirst();
    }

    public List<Item> items(Instant hour, String key, int limit) {
        return jdbc.query("""
                SELECT r.rank, r.unique_listener_count, t.* FROM ranking.hourly_track_rank r
                JOIN ranking.track t ON t.track_id = r.track_id
                WHERE ranked_hour = ? AND scope_key = ? ORDER BY r.rank LIMIT ?
                """, (rs, row) -> new Item(rs.getInt("rank"), rs.getLong("unique_listener_count"),
                new Track(rs.getString("track_id"), rs.getString("title"), rs.getString("artist_name"),
                        rs.getString("preview_url"), rs.getString("external_url"))), ts(hour), key, limit);
    }

    public void recordAttempt(Instant hour, Instant finishedAt, String status, long durationMs, String errorType) {
        jdbc.update("""
                INSERT INTO ranking.batch_attempt(ranked_hour, finished_at, status, duration_ms, error_type)
                VALUES (?, ?, ?, ?, ?)
                """, ts(hour), ts(finishedAt), status, durationMs, errorType);
    }

    private Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }
}
