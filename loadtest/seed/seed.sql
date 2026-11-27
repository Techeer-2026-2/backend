-- 부하 테스트 고정 데이터. 측정마다 reset.sql → seed.sql 순서로 넣어 데이터 양을 같게 유지한다.
--   회원 1,000명 (user_id 1~1000), 광고주 1명 (user_id 1), 진행 중 캠페인 100개 (campaign_id 1~100)
--   campaign_id 1 = 핫 캠페인: 모든 연령대·성별 + 입찰가가 높아 노출·클릭이 몰린다
-- 난수는 setseed 로 고정해서 매번 같은 데이터가 나온다.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
SELECT setseed(0.42);

-- 회원: 연령대 5종 고르게, 성별은 male/female 반반 + 10% 는 성별 없음(성별 제한 없는 캠페인만 매칭)
INSERT INTO users (email, password_hash, nickname, age_group, gender, created_at, updated_at)
SELECT 'lt-user-' || i || '@loadtest.local',
       'not-used',
       'lt-user-' || i,
       (ARRAY['10s','20s','30s','40s','50s'])[1 + (i % 5)],
       CASE WHEN i % 10 = 0 THEN NULL WHEN i % 2 = 0 THEN 'male' ELSE 'female' END,
       now(), now()
FROM generate_series(1, 1000) AS i;

-- 광고주: SSE 시나리오에서 로그인한다. 비밀번호는 loadtest/README.md 참고 (로컬 전용 값)
INSERT INTO advertisers (email, password_hash, business_name, plan, created_at, updated_at)
VALUES ('lt-advertiser@loadtest.local', crypt('loadtest-pass-1234', gen_salt('bf', 10)),
        'loadtest advertiser', 'FREE', now(), now());

-- 캠페인 100개. 예산은 측정 중 바닥나지 않게 크게 잡는다(예산 소진으로 204 가 늘면 비교가 깨진다).
INSERT INTO campaigns (user_id, title, body, image_url, link_url, time_start, time_end, weather_condition,
                       status, total_budget, bid_price, spent_total, target_clicks, created_at, updated_at)
SELECT 1,
       'lt-campaign-' || i,
       'load test campaign ' || i,
       'https://example.com/banner/' || i || '.png',
       'https://example.com/landing/' || i,
       NULL, NULL, NULL,
       'ACTIVE',
       1000000000000,
       CASE WHEN i = 1 THEN 5000 ELSE 100 + floor(random() * 900)::bigint END,
       0,
       1000000,
       now(), now()
FROM generate_series(1, 100) AS i;

-- 연령대 타겟: 핫 캠페인은 전부, 나머지는 1~3개를 무작위로
INSERT INTO campaign_age_groups (campaign_id, age_group)
SELECT 1, a FROM unnest(ARRAY['10s','20s','30s','40s','50s']) AS a;

INSERT INTO campaign_age_groups (campaign_id, age_group)
SELECT DISTINCT c, (ARRAY['10s','20s','30s','40s','50s'])[1 + floor(random() * 5)::int]
FROM generate_series(2, 100) AS c, generate_series(1, 3) AS k
WHERE k = 1 OR random() < 0.5;

-- 성별 타겟: 핫 캠페인은 all, 나머지는 all / male / female 섞기
INSERT INTO campaign_genders (campaign_id, gender)
SELECT c, CASE WHEN c = 1 THEN 'all' ELSE (ARRAY['all','male','female'])[1 + (c % 3)] END
FROM generate_series(1, 100) AS c;

-- 통계 행을 미리 만들어 둔다(첫 노출의 INSERT 경로와 이후 UPDATE 경로가 섞이지 않게)
INSERT INTO campaign_stats (campaign_id, sent_count, click_count, created_at, updated_at)
SELECT c, 0, 0, now(), now() FROM generate_series(1, 100) AS c;

ANALYZE;

SELECT (SELECT count(*) FROM users) AS users,
       (SELECT count(*) FROM campaigns) AS campaigns,
       (SELECT count(*) FROM campaign_age_groups) AS age_rows,
       (SELECT count(*) FROM campaign_genders) AS gender_rows;
