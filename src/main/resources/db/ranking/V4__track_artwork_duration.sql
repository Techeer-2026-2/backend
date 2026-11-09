-- 차트 응답에 앨범 커버와 곡 길이를 함께 내려주기 위한 곡 메타데이터 컬럼.
-- 기존 곡은 값이 없으므로 둘 다 nullable 로 둔다.
ALTER TABLE ranking.track ADD COLUMN artwork_url varchar(2048);
ALTER TABLE ranking.track ADD COLUMN track_time_millis integer CHECK (track_time_millis > 0);
