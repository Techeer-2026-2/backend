-- 클릭 조정: 하루치 S3 원본의 고유 건수와 ClickHouse 분 표 합계를 한 줄로 돌려준다 (고도화 2026-11)
-- 내용은 담당 B, 실행과 recompute_log 기록은 담당 C (ReconcileJob)
-- 재계산이 끝난 뒤에는 두 값이 같아야 한다 (s3_unique_count = ch_daily_count)
--
-- 실행 코드가 바꿔 넣는 자리: ${day}, ${raw_source} (recompute_clicks.sql 과 같은 값)
SELECT
    (SELECT uniqExact(notification_id)
     FROM ${raw_source}
     WHERE toDate(event_at) = toDate('${day}')
     SETTINGS input_format_skip_unknown_fields = 1) AS s3_unique_count,
    (SELECT sum(cnt)
     FROM click_facts
     WHERE toDate(minute) = toDate('${day}')) AS ch_daily_count
