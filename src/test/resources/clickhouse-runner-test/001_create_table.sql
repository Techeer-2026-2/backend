-- ClickHouseSqlRunner 테스트용. 표를 만든다
CREATE TABLE IF NOT EXISTS runner_test_events (
    campaign_id Int64,
    cnt         UInt64
) ENGINE = MergeTree
ORDER BY campaign_id;
