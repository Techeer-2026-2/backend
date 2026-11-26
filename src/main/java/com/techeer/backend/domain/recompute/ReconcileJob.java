package com.techeer.backend.domain.recompute;

import static com.techeer.backend.domain.recompute.RecomputeSql.EventKind.CLICK;
import static com.techeer.backend.domain.recompute.RecomputeSql.EventKind.IMPRESSION;

import java.time.LocalDate;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(ReconcileJob.class);

    private final RecomputeSql sql;
    private final PipelineLogRepository logs;

    ReconcileJob(RecomputeSql sql, PipelineLogRepository logs) {
        this.sql = sql;
        this.logs = logs;
    }

    void run(LocalDate from, LocalDate to) {
        logs.serializeJobs(() -> {
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                reconcile(date);
            }
        });
    }

    private void reconcile(LocalDate date) {
        var run = logs.latestRun(date);
        if (run == null) {
            long id = logs.start(date);
            logs.fail(id, "No recompute run before reconciliation");
            run = new PipelineLogRepository.Run(id, "FAILED");
        }
        try {
            var archives = logs.archives(date);
            var clicks = sql.archive(date, CLICK);
            var impressions = sql.archive(date, IMPRESSION);
            long s3Lines = clicks.lines() + impressions.lines();
            long s3Unique = clicks.unique() + impressions.unique();
            long clickDaily = sql.daily(date, CLICK);
            long impressionDaily = sql.daily(date, IMPRESSION);
            long pgImpressions = logs.impressions(date);
            var errors = new ArrayList<String>();
            if (!"SUCCESS".equals(run.status())) {
                errors.add("Recompute status is " + run.status());
            }
            if (archives.consumed() != archives.written() || archives.written() != s3Lines
                    || archives.files() != clicks.files() + impressions.files()) {
                errors.add("Archive counts differ between Kafka, raw_archive_batch and S3");
            }
            if (clicks.unique() != clickDaily || impressions.unique() != impressionDaily) {
                errors.add("S3 and ClickHouse event totals differ");
            }
            if (pgImpressions != impressions.unique()) {
                errors.add("PostgreSQL and S3 impression totals differ");
            }
            String error = errors.isEmpty() ? null : String.join(". ", errors);
            logs.recordReconciliation(run.id(), archives.consumed(), s3Lines, s3Unique, clickDaily + impressionDaily,
                    pgImpressions, errors.isEmpty(), error);
            if (error != null) {
                log.warn("Reconciliation {} failed: {}", date, error);
            }
        } catch (RuntimeException e) {
            log.warn("Reconciliation {} could not read counts", date, e);
            logs.recordReconciliation(run.id(), null, null, null, null, null, false,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
