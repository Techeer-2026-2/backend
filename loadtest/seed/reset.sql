-- 부하 테스트 DB 를 매 측정 전 같은 상태로 되돌린다. 부하 테스트 전용 DB 에서만 실행한다.
-- 테이블은 백엔드가 한 번 기동하면서(ddl-auto: update) 만든 뒤라야 한다.
DO $$
BEGIN
  IF current_database() NOT LIKE '%loadtest%' THEN
    RAISE EXCEPTION '부하 테스트 DB(이름에 loadtest 포함)에서만 실행할 수 있습니다. 현재: %', current_database();
  END IF;
END $$;

TRUNCATE click_events, ad_impressions, campaign_stats,
         campaign_age_groups, campaign_genders, campaigns,
         advertisers, users
  RESTART IDENTITY CASCADE;
