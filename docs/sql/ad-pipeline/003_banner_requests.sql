-- 003: 배너 요청 ID 표 (같은 요청 중복 차감 방지). 두 번 실행해도 같은 결과.
CREATE TABLE IF NOT EXISTS banner_requests (
    request_id      VARCHAR(64) PRIMARY KEY,
    target_user_id  BIGINT      NOT NULL,
    notification_id VARCHAR(36),
    created_at      TIMESTAMP   NOT NULL
);
