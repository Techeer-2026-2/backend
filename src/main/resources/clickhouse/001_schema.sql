-- 광고 집계 ClickHouse 스키마 (고도화 2026-11)
-- 모든 문장은 IF NOT EXISTS 라서 여러 번 실행해도 결과가 같다.
-- 문장 구분은 세미콜론으로 한다. 주석 안에는 세미콜론을 쓰지 않는다 (실행기가 세미콜론으로 문장을 나눈다).

-- 1. Kafka 를 읽는 통로 (저장하지 않음). 토픽 파티션 3개라 소비자도 3개
--    클릭 메시지에는 target_user_id 가 더 있어서, 모르는 필드는 건너뛰게 한다 (input_format_skip_unknown_fields)
CREATE TABLE IF NOT EXISTS impressions_kafka (
    notification_id String,
    campaign_id     Int64,
    age_group       String,
    gender          String,
    event_at        DateTime
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'ad-impressed',
         kafka_group_name = 'clickhouse-impressions',
         kafka_format = 'JSONEachRow',
         kafka_num_consumers = 3,
         input_format_skip_unknown_fields = 1;

CREATE TABLE IF NOT EXISTS clicks_kafka (
    notification_id String,
    campaign_id     Int64,
    age_group       String,
    gender          String,
    event_at        DateTime
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'ad-clicked',
         kafka_group_name = 'clickhouse-clicks',
         kafka_format = 'JSONEachRow',
         kafka_num_consumers = 3,
         input_format_skip_unknown_fields = 1;

-- 2. 분 표 (실시간, 잠정). 같은 키의 숫자는 병합할 때 합산된다
CREATE TABLE IF NOT EXISTS impression_facts (
    campaign_id Int64,
    minute      DateTime,
    age_group   LowCardinality(String),
    gender      LowCardinality(String),
    cnt         UInt64
) ENGINE = SummingMergeTree
PARTITION BY toYYYYMMDD(minute)
ORDER BY (campaign_id, minute, age_group, gender);

CREATE TABLE IF NOT EXISTS click_facts (
    campaign_id Int64,
    minute      DateTime,
    age_group   LowCardinality(String),
    gender      LowCardinality(String),
    cnt         UInt64
) ENGINE = SummingMergeTree
PARTITION BY toYYYYMMDD(minute)
ORDER BY (campaign_id, minute, age_group, gender);

-- 3. 시간 표, 일 표 (분 표를 묶은 롤업)
CREATE TABLE IF NOT EXISTS impression_facts_hour (
    campaign_id Int64,
    hour        DateTime,
    age_group   LowCardinality(String),
    gender      LowCardinality(String),
    cnt         UInt64
) ENGINE = SummingMergeTree
PARTITION BY toYYYYMMDD(hour)
ORDER BY (campaign_id, hour, age_group, gender);

CREATE TABLE IF NOT EXISTS impression_facts_day (
    campaign_id Int64,
    day         Date,
    age_group   LowCardinality(String),
    gender      LowCardinality(String),
    cnt         UInt64
) ENGINE = SummingMergeTree
PARTITION BY toYYYYMMDD(day)
ORDER BY (campaign_id, day, age_group, gender);

CREATE TABLE IF NOT EXISTS click_facts_hour (
    campaign_id Int64,
    hour        DateTime,
    age_group   LowCardinality(String),
    gender      LowCardinality(String),
    cnt         UInt64
) ENGINE = SummingMergeTree
PARTITION BY toYYYYMMDD(hour)
ORDER BY (campaign_id, hour, age_group, gender);

CREATE TABLE IF NOT EXISTS click_facts_day (
    campaign_id Int64,
    day         Date,
    age_group   LowCardinality(String),
    gender      LowCardinality(String),
    cnt         UInt64
) ENGINE = SummingMergeTree
PARTITION BY toYYYYMMDD(day)
ORDER BY (campaign_id, day, age_group, gender);

-- 4. 재계산용 임시 표 (같은 구조, 재계산할 때만 채운다)
CREATE TABLE IF NOT EXISTS impression_facts_stage      AS impression_facts;
CREATE TABLE IF NOT EXISTS click_facts_stage           AS click_facts;
CREATE TABLE IF NOT EXISTS impression_facts_hour_stage AS impression_facts_hour;
CREATE TABLE IF NOT EXISTS impression_facts_day_stage  AS impression_facts_day;
CREATE TABLE IF NOT EXISTS click_facts_hour_stage      AS click_facts_hour;
CREATE TABLE IF NOT EXISTS click_facts_day_stage       AS click_facts_day;

-- 5. 실시간 MV. Kafka 메시지 묶음이 들어올 때마다 분 표에 더한다
CREATE MATERIALIZED VIEW IF NOT EXISTS impression_facts_mv TO impression_facts AS
SELECT campaign_id, toStartOfMinute(event_at) AS minute, age_group, gender, count() AS cnt
FROM impressions_kafka
GROUP BY campaign_id, minute, age_group, gender;

CREATE MATERIALIZED VIEW IF NOT EXISTS click_facts_mv TO click_facts AS
SELECT campaign_id, toStartOfMinute(event_at) AS minute, age_group, gender, count() AS cnt
FROM clicks_kafka
GROUP BY campaign_id, minute, age_group, gender;

-- 6. 롤업 MV. 분 표에 행이 추가될 때마다 시간 표, 일 표에 더한다
--    재계산이 분 표를 교체해도 시간 표, 일 표는 자동으로 바뀌지 않는다. 재계산 SQL 이 직접 교체한다
CREATE MATERIALIZED VIEW IF NOT EXISTS impression_facts_hour_mv TO impression_facts_hour AS
SELECT campaign_id, toStartOfHour(minute) AS hour, age_group, gender, sum(cnt) AS cnt
FROM impression_facts
GROUP BY campaign_id, hour, age_group, gender;

CREATE MATERIALIZED VIEW IF NOT EXISTS impression_facts_day_mv TO impression_facts_day AS
SELECT campaign_id, toDate(minute) AS day, age_group, gender, sum(cnt) AS cnt
FROM impression_facts
GROUP BY campaign_id, day, age_group, gender;

CREATE MATERIALIZED VIEW IF NOT EXISTS click_facts_hour_mv TO click_facts_hour AS
SELECT campaign_id, toStartOfHour(minute) AS hour, age_group, gender, sum(cnt) AS cnt
FROM click_facts
GROUP BY campaign_id, hour, age_group, gender;

CREATE MATERIALIZED VIEW IF NOT EXISTS click_facts_day_mv TO click_facts_day AS
SELECT campaign_id, toDate(minute) AS day, age_group, gender, sum(cnt) AS cnt
FROM click_facts
GROUP BY campaign_id, day, age_group, gender;
