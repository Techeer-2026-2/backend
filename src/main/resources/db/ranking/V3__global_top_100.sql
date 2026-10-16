-- 전체 차트를 100위까지 저장할 수 있게 순위 제약을 교체한다. (V2 가 만든 1~50 제약을 대체)
ALTER TABLE ranking.hourly_track_rank
    DROP CONSTRAINT IF EXISTS hourly_track_rank_rank_check;

ALTER TABLE ranking.hourly_track_rank
    ADD CONSTRAINT hourly_track_rank_rank_check CHECK (rank BETWEEN 1 AND 100);
