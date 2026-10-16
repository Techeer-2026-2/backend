ALTER TABLE ranking.hourly_track_rank
    DROP CONSTRAINT IF EXISTS hourly_track_rank_rank_check;

ALTER TABLE ranking.hourly_track_rank
    ADD CONSTRAINT hourly_track_rank_rank_check CHECK (rank BETWEEN 1 AND 100);
