-- ClickHouseSqlRunner 테스트용. 001 의 표가 먼저 있어야 만들어지므로 파일 이름 순서대로 실행되는지 확인할 수 있다
CREATE VIEW IF NOT EXISTS runner_test_view AS
SELECT campaign_id, sum(cnt) AS cnt
FROM runner_test_events
GROUP BY campaign_id;

-- 한 파일에 문장 두 개
CREATE TABLE IF NOT EXISTS runner_test_second (
    id Int64
) ENGINE = MergeTree
ORDER BY id;
