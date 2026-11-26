package com.techeer.backend.domain.recompute;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    ArchiveTotals archives(LocalDate date) {
        return jdbc.queryForObject("""
                SELECT count(*), coalesce(sum(consumed_count), 0), coalesce(sum(written_count), 0)
                FROM raw_archive_batch WHERE event_date = ?
                """, (rs, row) -> new ArchiveTotals(rs.getInt(1), rs.getLong(2), rs.getLong(3)), date);
    }

    record ArchiveTotals(int files, long consumed, long written) {
    }
}
