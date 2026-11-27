package com.techeer.backend.domain.recompute;

import static com.techeer.backend.domain.recompute.RecomputeSql.EventKind.CLICK;
import static com.techeer.backend.domain.recompute.RecomputeSql.EventKind.IMPRESSION;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RecomputeJob {

    private static final Logger log = LoggerFactory.getLogger(RecomputeJob.class);

    private final RecomputeSql sql;
    private final PipelineLogRepository logs;

    RecomputeJob(RecomputeSql sql, PipelineLogRepository logs) {
        this.sql = sql;
        this.logs = logs;
    }

    boolean run(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("recompute.from must be on or before recompute.to");
        }
        var success = new AtomicBoolean(true);
        logs.serializeJobs(() -> {
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                if (!recompute(date)) {
                    success.set(false);
                }
            }
        });
        return success.get();
    }

    private boolean recompute(LocalDate date) {
        long id = logs.start(date);
        Long before = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                if (before == null) {
                    before = sql.daily(date, CLICK) + sql.daily(date, IMPRESSION);
                }
                var clicks = sql.archive(date, CLICK);
                var impressions = sql.archive(date, IMPRESSION);
                sql.replace(date, CLICK);
                sql.replace(date, IMPRESSION);
                long after = sql.daily(date, CLICK) + sql.daily(date, IMPRESSION);
                long duplicates = clicks.lines() - clicks.unique() + impressions.lines() - impressions.unique();
                logs.complete(id, clicks.files() + impressions.files(), clicks.unique(), impressions.unique(),
                        duplicates, before, after);
                log.info("Recompute {} succeeded: duplicates={}, count before={}, count after={}", date,
                        duplicates, before, after);
                return true;
            } catch (RuntimeException e) {
                log.warn("Recompute {} failed on attempt {}", date, attempt, e);
                if (attempt == 2) {
                    logs.fail(id, e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        }
        return false;
    }
}
