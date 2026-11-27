BEGIN;

CREATE TABLE IF NOT EXISTS recompute_log (
    recompute_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    target_date DATE NOT NULL,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    status VARCHAR(16) NOT NULL,
    files_read INT,
    unique_clicks BIGINT,
    unique_impressions BIGINT,
    duplicate_count BIGINT,
    previous_total BIGINT,
    replaced_total BIGINT,
    kafka_read_count BIGINT,
    s3_line_count BIGINT,
    s3_unique_count BIGINT,
    ch_daily_count BIGINT,
    pg_impression_count BIGINT,
    reconcile_ok BOOLEAN,
    error_message TEXT,
    UNIQUE (target_date, started_at)
);

CREATE TABLE IF NOT EXISTS raw_archive_batch (
    batch_id VARCHAR(64) PRIMARY KEY,
    topic VARCHAR(32) NOT NULL,
    event_date DATE NOT NULL,
    consumed_count BIGINT NOT NULL,
    written_count BIGINT NOT NULL,
    s3_key VARCHAR(512) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_raw_batch_date ON raw_archive_batch (event_date, topic);

COMMIT;
