-- V1이 자동 생성한 순위 제약을 교체해 기존 DB도 50위까지 저장할 수 있게 한다.
ALTER TABLE ranking.hourly_track_rank
    DROP CONSTRAINT IF EXISTS hourly_track_rank_rank_check;

ALTER TABLE ranking.hourly_track_rank
    ADD CONSTRAINT hourly_track_rank_rank_check CHECK (rank BETWEEN 1 AND 50);
