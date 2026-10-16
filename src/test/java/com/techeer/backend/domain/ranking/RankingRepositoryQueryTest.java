package com.techeer.backend.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.ranking.repository.RankingRepository;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 전체 차트 조회 SQL(곡별 20명 미만 제외, 직전 한 시간과의 순위 비교)을 실제 PostgreSQL 로 확인한다.
 * 원래 H2 로 작성된 PR #103 의 테스트를 프로젝트 규칙에 맞게 실제 DB 로 옮겼다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RankingRepositoryQueryTest {
    private static final Instant HOUR = Instant.parse("2026-10-03T02:00:00Z");

    @Autowired private RankingRepository repository;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void resetData() {
        jdbc.execute("TRUNCATE ranking.playback_event, ranking.playback_session, ranking.latest_user_location, "
                + "ranking.hourly_track_rank, ranking.hourly_chart, ranking.batch, ranking.batch_attempt, "
                + "ranking.city_cell, ranking.track CASCADE");
        for (String id : new String[]{"A", "B", "C", "D"}) {
            jdbc.update("INSERT INTO ranking.track(track_id, title, artist_name) VALUES (?, ?, ?)", id, id, "artist");
        }
    }

    @Test
    void globalQueryFiltersBelowTwentyAndComparesOnlyPreviousHour() {
        Instant previous = HOUR.minusSeconds(3600);
        insertChart(previous, 24);
        insertRank(previous, "A", 1, 22);
        insertRank(previous, "B", 2, 20);
        insertChart(HOUR, 24);
        insertRank(HOUR, "B", 1, 22);
        insertRank(HOUR, "A", 2, 20);
        insertRank(HOUR, "D", 3, 20);
        insertRank(HOUR, "C", 4, 19);

        var items = repository.items(HOUR, "GLOBAL", 100);

        assertThat(items).hasSize(3);
        assertThat(items.get(0).track().id()).isEqualTo("B");
        assertThat(items.get(0).rankChange()).isEqualTo(1);
        assertThat(items.get(1).track().id()).isEqualTo("A");
        assertThat(items.get(1).rankChange()).isEqualTo(-1);
        assertThat(items.get(2).track().id()).isEqualTo("D");
        assertThat(items.get(2).rankChange()).isNull();
        assertThat(repository.eligibleLabel(HOUR, "GLOBAL")).contains("전체 인기곡");
    }

    @Test
    void globalChartWithOnlyTracksBelowTwentyIsNotEligible() {
        Instant belowThresholdHour = HOUR.plusSeconds(3600);
        insertChart(belowThresholdHour, 24);
        insertRank(belowThresholdHour, "C", 1, 19);

        assertThat(repository.eligibleLabel(belowThresholdHour, "GLOBAL")).isEmpty();
        assertThat(repository.items(belowThresholdHour, "GLOBAL", 100)).isEmpty();
    }

    @Test
    void ranksUpTo100AreStoredAfterTheMigration() {
        insertChart(HOUR, 24);
        for (int i = 1; i <= 100; i++) {
            String id = "T%03d".formatted(i);
            jdbc.update("INSERT INTO ranking.track(track_id, title, artist_name) VALUES (?, ?, ?)", id, id, "artist");
            insertRank(HOUR, id, i, 20);
        }

        assertThat(repository.items(HOUR, "GLOBAL", 100)).hasSize(100);
    }

    private void insertChart(Instant hour, long listeners) {
        jdbc.update("INSERT INTO ranking.batch(ranked_hour, updated_at, event_count, duration_ms) VALUES (?, ?, 0, 0)",
                Timestamp.from(hour), Timestamp.from(hour.plusSeconds(10)));
        jdbc.update("INSERT INTO ranking.hourly_chart(ranked_hour, scope_key, location_label, listener_count) "
                + "VALUES (?, 'GLOBAL', '전체 인기곡', ?)", Timestamp.from(hour), listeners);
    }

    private void insertRank(Instant hour, String track, int rank, int listeners) {
        jdbc.update("INSERT INTO ranking.hourly_track_rank(ranked_hour, scope_key, track_id, rank, "
                + "unique_listener_count, completion_count, last_completed_at) VALUES (?, 'GLOBAL', ?, ?, ?, ?, ?)",
                Timestamp.from(hour), track, rank, listeners, listeners, Timestamp.from(hour));
    }
}
