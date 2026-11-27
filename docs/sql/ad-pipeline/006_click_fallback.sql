-- 006: 클릭 발행 실패분 보관. 두 번 실행해도 같은 결과.
CREATE TABLE IF NOT EXISTS click_fallback (
    fallback_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    notification_id VARCHAR(36) NOT NULL,
    payload         TEXT        NOT NULL,
    status          VARCHAR(16) NOT NULL,
    created_at      TIMESTAMP   NOT NULL,
    sent_at         TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_click_fallback_pending ON click_fallback (status, fallback_id);
