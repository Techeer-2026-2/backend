package com.techeer.backend.domain.recompute;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

final class PipelineLogRepository {

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final Clock clock;

    PipelineLogRepository(JdbcTemplate jdbc, DataSource dataSource, Clock clock) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.clock = clock;
    }

    void recordArchive(RawBatchBuffer.ArchiveFile file) {
        jdbc.update("""
                INSERT INTO raw_archive_batch (batch_id, topic, event_date, consumed_count, written_count, s3_key, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, file.id().toString(), file.bucket().topic(), file.bucket().hour().toLocalDate(),
                file.lines().size(), file.lines().size(), file.key(), LocalDateTime.now(clock));
    }

    long start(LocalDate date) {
        return jdbc.queryForObject("""
                INSERT INTO recompute_log (target_date, started_at, status)
                VALUES (?, ?, 'RUNNING') RETURNING recompute_id
                """, Long.class, date, LocalDateTime.now(clock));
    }

    void complete(long id, int files, long clicks, long impressions, long duplicates, long before, long after) {
        jdbc.update("""
                UPDATE recompute_log
                SET status = 'SUCCESS', finished_at = ?, files_read = ?, unique_clicks = ?,
                    unique_impressions = ?, duplicate_count = ?, previous_total = ?, replaced_total = ?
                WHERE recompute_id = ?
                """, LocalDateTime.now(clock), files, clicks, impressions, duplicates, before, after, id);
    }

    void fail(long id, String error) {
        jdbc.update("""
                UPDATE recompute_log SET status = 'FAILED', finished_at = ?, error_message = ? WHERE recompute_id = ?
                """, LocalDateTime.now(clock), error, id);
    }

    Run latestRun(LocalDate date) {
        List<Run> runs = jdbc.query("""
                SELECT recompute_id, status FROM recompute_log
                WHERE target_date = ? ORDER BY started_at DESC, recompute_id DESC LIMIT 1
                """, (rs, row) -> new Run(rs.getLong(1), rs.getString(2)), date);
        return runs.isEmpty() ? null : runs.getFirst();
    }

    ArchiveTotals archives(LocalDate date) {
        return jdbc.queryForObject("""
                SELECT count(*), coalesce(sum(consumed_count), 0), coalesce(sum(written_count), 0)
                FROM raw_archive_batch WHERE event_date = ?
                """, (rs, row) -> new ArchiveTotals(rs.getInt(1), rs.getLong(2), rs.getLong(3)), date);
    }

    long impressions(LocalDate date) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM ad_impressions WHERE shown_at >= ? AND shown_at < ?
                """, Long.class, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }

    void recordReconciliation(long id, Long consumed, Long s3Lines, Long unique, Long daily, Long impressions,
            boolean ok, String error) {
        jdbc.update("""
                UPDATE recompute_log
                SET kafka_read_count = ?, s3_line_count = ?, s3_unique_count = ?, ch_daily_count = ?,
                    pg_impression_count = ?, reconcile_ok = ?,
                    error_message = CASE WHEN status = 'FAILED' THEN coalesce(error_message, ?) ELSE ? END
                WHERE recompute_id = ?
                """, consumed, s3Lines, unique, daily, impressions, ok, error, error, id);
    }

    void serializeJobs(Runnable job) {
        // A separate PostgreSQL transaction holds the lock while the log writes commit independently.
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(1904004)")) {
                    statement.execute();
                }
                job.run();
            } finally {
                connection.rollback();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not hold the recompute job lock", e);
        }
    }

    record Run(long id, String status) {
    }

    record ArchiveTotals(int files, long consumed, long written) {
    }
}
