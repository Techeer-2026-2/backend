TRUNCATE TABLE {{facts}}_stage;
INSERT INTO {{facts}}_stage
SELECT campaign_id, toStartOfMinute(event_at) AS minute, age_group, gender, count() AS cnt
FROM (
    SELECT notification_id, any(campaign_id) AS campaign_id, any(age_group) AS age_group,
        any(gender) AS gender, any(event_at) AS event_at
    FROM s3({{s3_url}}, NOSIGN, 'JSONEachRow',
        'notification_id String, campaign_id Int64, age_group String, gender String, event_at DateTime')
    GROUP BY notification_id
)
GROUP BY campaign_id, minute, age_group, gender
SETTINGS s3_throw_on_zero_files_match = 0;
ALTER TABLE {{facts}} REPLACE PARTITION {{partition}} FROM {{facts}}_stage;

TRUNCATE TABLE {{facts}}_hour_stage;
INSERT INTO {{facts}}_hour_stage
SELECT campaign_id, toStartOfHour(minute) AS hour, age_group, gender, sum(cnt)
FROM {{facts}} WHERE toDate(minute) = {{date}}
GROUP BY campaign_id, hour, age_group, gender;
ALTER TABLE {{facts}}_hour REPLACE PARTITION {{partition}} FROM {{facts}}_hour_stage;

TRUNCATE TABLE {{facts}}_day_stage;
INSERT INTO {{facts}}_day_stage
SELECT campaign_id, toDate(minute) AS day, age_group, gender, sum(cnt)
FROM {{facts}} WHERE toDate(minute) = {{date}}
GROUP BY campaign_id, day, age_group, gender;
ALTER TABLE {{facts}}_day REPLACE PARTITION {{partition}} FROM {{facts}}_day_stage;
