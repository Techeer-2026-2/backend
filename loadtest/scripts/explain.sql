-- 핵심 쿼리 실행 계획. 단계마다 같은 값으로 실행해 비교한다.
-- 1) 배너 후보 조회 (CampaignRepository.findActiveForTarget)
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM campaigns c
WHERE c.status = 'ACTIVE'
  AND c.deleted_at IS NULL
  AND c.spent_total < c.total_budget
  AND (c.time_start IS NULL OR c.time_start <= now())
  AND (c.time_end IS NULL OR c.time_end >= now())
  AND EXISTS (SELECT 1 FROM campaign_age_groups a WHERE a.campaign_id = c.campaign_id AND a.age_group = '20s')
  AND EXISTS (SELECT 1 FROM campaign_genders g WHERE g.campaign_id = c.campaign_id AND g.gender IN ('all', 'male'))
ORDER BY c.campaign_id;

-- 2) 실시간 통계 조회 (SSE 가 2초마다 실행)
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM campaign_stats WHERE campaign_id = 1;

-- P3(분 버킷) 이후에는 분 버킷 집계 쿼리를 여기에 추가한다.
