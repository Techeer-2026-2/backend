-- 001: 연령대·성별·예산 열 추가. 두 번 실행해도 같은 결과 (IF NOT EXISTS).
-- 엔티티(AdImpression, Campaign)에는 이미 열이 있으므로 운영 DB에 없을 때만 추가된다.
-- 적용 전 확인: \d ad_impressions, \d click_events, \d campaigns

ALTER TABLE ad_impressions
    ADD COLUMN IF NOT EXISTS age_group VARCHAR(16),
    ADD COLUMN IF NOT EXISTS gender VARCHAR(8),
    ADD COLUMN IF NOT EXISTS charged_amount BIGINT NOT NULL DEFAULT 0;

ALTER TABLE click_events
    ADD COLUMN IF NOT EXISTS age_group VARCHAR(16),
    ADD COLUMN IF NOT EXISTS gender VARCHAR(8);

ALTER TABLE campaigns
    ADD COLUMN IF NOT EXISTS total_budget BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS bid_price BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS spent_total BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS target_clicks BIGINT NOT NULL DEFAULT 0;
