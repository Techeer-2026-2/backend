package com.techeer.backend.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import com.techeer.backend.domain.ranking.repository.RankingRepository;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class RankingRepositoryQueryTest {
    private static final Instant HOUR = Instant.parse("2026-10-03T02:00:00Z");

    @Test
    void globalQueryFiltersBelowTwentyAndComparesOnlyPreviousHour() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:ranking_query;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS ranking");
        jdbc.execute("CREATE TABLE ranking.track (track_id varchar(128) PRIMARY KEY, "
                + "title varchar(256), artist_name varchar(256), preview_url varchar(2048), external_url varchar(2048))");
        jdbc.execute("CREATE TABLE ranking.hourly_chart (ranked_hour timestamp with time zone, "
                + "scope_key varchar(128), location_label varchar(256), listener_count bigint)");
        jdbc.execute("CREATE TABLE ranking.hourly_track_rank (ranked_hour timestamp with time zone, "
                + "scope_key varchar(128), track_id varchar(128), rank integer, unique_listener_count bigint)");
        for (String id : new String[]{"A", "B", "C", "D"}) {
            jdbc.update("INSERT INTO ranking.track(track_id,title,artist_name) VALUES (?,?,?)", id, id, "artist");
        }
        Instant previous = HOUR.minusSeconds(3600);
        insertRank(jdbc, previous, "A", 1, 22);
        insertRank(jdbc, previous, "B", 2, 20);
        insertRank(jdbc, HOUR, "B", 1, 22);
        insertRank(jdbc, HOUR, "A", 2, 20);
        insertRank(jdbc, HOUR, "D", 3, 20);
        insertRank(jdbc, HOUR, "C", 4, 19);
        jdbc.update("INSERT INTO ranking.hourly_chart VALUES (?,?,?,?)",
                Timestamp.from(HOUR), "GLOBAL", "전체 인기곡", 24);

        RankingRepository repository = new RankingRepository(jdbc);
        var items = repository.items(HOUR, "GLOBAL", 100);
        assertThat(items).hasSize(3);
        assertThat(items.get(0).track().id()).isEqualTo("B");
        assertThat(items.get(0).rankChange()).isEqualTo(1);
        assertThat(items.get(1).rankChange()).isEqualTo(-1);
        assertThat(items.get(2).rankChange()).isNull();
        assertThat(repository.eligibleLabel(HOUR, "GLOBAL")).contains("전체 인기곡");

        Instant belowThresholdHour = HOUR.plusSeconds(3600);
        insertRank(jdbc, belowThresholdHour, "C", 1, 19);
        jdbc.update("INSERT INTO ranking.hourly_chart VALUES (?,?,?,?)",
                Timestamp.from(belowThresholdHour), "GLOBAL", "전체 인기곡", 24);
        assertThat(repository.eligibleLabel(belowThresholdHour, "GLOBAL")).isEmpty();
    }

    private void insertRank(JdbcTemplate jdbc, Instant hour, String track, int rank, int listeners) {
        jdbc.update("INSERT INTO ranking.hourly_track_rank VALUES (?,?,?,?,?)",
                Timestamp.from(hour), "GLOBAL", track, rank, listeners);
    }
}
