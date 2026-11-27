-- 노출 재계산: 하루치 S3 원본으로 분, 시간, 일 표의 그 날짜 파티션을 교체한다 (고도화 2026-11)
-- 내용은 담당 B, 실행 코드는 담당 C (RecomputeJob). 같은 날짜를 두 번 실행해도 결과가 같다 (교체 방식)
-- 중간에 실패하면 그 날짜를 처음부터 다시 실행한다
--
-- 실행 코드가 바꿔 넣는 자리
--   ${day}         대상 날짜 yyyy-MM-dd   예) 2026-11-21
--   ${partition}   대상 파티션 yyyyMMdd   예) 20261121
--   ${raw_source}  원본을 읽는 테이블 함수. 운영 값 예)
--                  s3('https://<RAW_S3_BUCKET>.s3.<RAW_S3_REGION>.amazonaws.com/raw-impressions/2026/11/21/*/*.json', 'JSONEachRow', 'notification_id String, campaign_id Int64, age_group String, gender String, event_at DateTime')
--                  테스트에서는 같은 컬럼을 가진 표 이름을 넣는다
-- 문장 구분은 세미콜론, 주석 안에는 세미콜론을 쓰지 않는다

-- 1. 임시 표 비우기
TRUNCATE TABLE impression_facts_stage;

-- 2. 원본에서 notification_id 로 중복을 없앤 뒤 분, 연령대, 성별로 센다
INSERT INTO impression_facts_stage
SELECT campaign_id, toStartOfMinute(event_at) AS minute, age_group, gender, count() AS cnt
FROM (
    SELECT notification_id,
           any(campaign_id) AS campaign_id,
           any(age_group)   AS age_group,
           any(gender)      AS gender,
           any(event_at)    AS event_at
    FROM ${raw_source}
    GROUP BY notification_id
)
WHERE toDate(event_at) = toDate('${day}')
GROUP BY campaign_id, minute, age_group, gender
SETTINGS input_format_skip_unknown_fields = 1;

-- 3. 분 표의 그 날짜 파티션 교체
ALTER TABLE impression_facts REPLACE PARTITION ${partition} FROM impression_facts_stage;

-- 4. 시간 표 다시 만들어 교체 (분 표를 교체해도 롤업 MV 는 다시 돌지 않는다)
TRUNCATE TABLE impression_facts_hour_stage;

INSERT INTO impression_facts_hour_stage
SELECT campaign_id, toStartOfHour(minute) AS hour, age_group, gender, sum(cnt) AS cnt
FROM impression_facts
WHERE toDate(minute) = toDate('${day}')
GROUP BY campaign_id, hour, age_group, gender;

ALTER TABLE impression_facts_hour REPLACE PARTITION ${partition} FROM impression_facts_hour_stage;

-- 5. 일 표 다시 만들어 교체
TRUNCATE TABLE impression_facts_day_stage;

INSERT INTO impression_facts_day_stage
SELECT campaign_id, toDate(minute) AS day, age_group, gender, sum(cnt) AS cnt
FROM impression_facts
WHERE toDate(minute) = toDate('${day}')
GROUP BY campaign_id, day, age_group, gender;

ALTER TABLE impression_facts_day REPLACE PARTITION ${partition} FROM impression_facts_day_stage;
