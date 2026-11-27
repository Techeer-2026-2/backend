-- 002: 아웃박스 (노출 이벤트 발행 대기). 두 번 실행해도 같은 결과.
CREATE TABLE IF NOT EXISTS outbox (
    outbox_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type      VARCHAR(32) NOT NULL,
    notification_id VARCHAR(36) NOT NULL,
    payload         TEXT        NOT NULL,
    status          VARCHAR(16) NOT NULL,
    created_at      TIMESTAMP   NOT NULL,
    published_at    TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_outbox_pending ON outbox (status, outbox_id);
