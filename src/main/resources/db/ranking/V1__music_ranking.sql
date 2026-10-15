CREATE TABLE ranking.track (
    track_id varchar(128) PRIMARY KEY,
    title varchar(256) NOT NULL,
    artist_name varchar(256) NOT NULL,
    preview_url varchar(2048),
    external_url varchar(2048)
);

-- 운영자가 검증한 H3 r8 → 행정 도시 매핑. 클라이언트의 도시명은 신뢰하지 않는다.
CREATE TABLE ranking.city_cell (
    h3_cell varchar(15) PRIMARY KEY,
    city_id varchar(64) NOT NULL,
    city_label varchar(128) NOT NULL
);
CREATE INDEX city_cell_city_idx ON ranking.city_cell(city_id);

CREATE TABLE ranking.latest_user_location (
    user_id bigint PRIMARY KEY,
    lat double precision NOT NULL CHECK (lat BETWEEN -90 AND 90),
    lng double precision NOT NULL CHECK (lng BETWEEN -180 AND 180),
    accuracy_meters double precision NOT NULL CHECK (accuracy_meters >= 0),
    recorded_at timestamptz NOT NULL,
    h3_cell varchar(15) NOT NULL
);

CREATE TABLE ranking.playback_session (
    session_id uuid PRIMARY KEY,
    user_id bigint NOT NULL,
    track_id varchar(128) NOT NULL REFERENCES ranking.track(track_id),
    started_at timestamptz NOT NULL,
    h3_cell varchar(15),
    city_id varchar(64),
    city_label varchar(128),
    location_status varchar(32) NOT NULL,
    request_hash varchar(64) NOT NULL
);

CREATE TABLE ranking.playback_event (
    event_id uuid PRIMARY KEY,
    session_id uuid NOT NULL UNIQUE REFERENCES ranking.playback_session(session_id),
    event_type varchar(32) NOT NULL CHECK (event_type IN ('TRACK_COMPLETED', 'TRACK_SKIPPED')),
    played_ratio double precision NOT NULL CHECK (played_ratio BETWEEN 0 AND 1),
    occurred_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL,
    request_hash varchar(64) NOT NULL
);
CREATE INDEX playback_event_hour_idx ON ranking.playback_event(occurred_at)
    WHERE event_type = 'TRACK_COMPLETED' AND played_ratio >= 0.9;

-- 한 시간의 모든 scope와 항목을 하나의 트랜잭션으로 교체한다.
CREATE TABLE ranking.batch (
    ranked_hour timestamptz PRIMARY KEY,
    updated_at timestamptz NOT NULL,
    event_count bigint NOT NULL,
    duration_ms bigint NOT NULL
);
CREATE TABLE ranking.hourly_chart (
    ranked_hour timestamptz NOT NULL REFERENCES ranking.batch(ranked_hour) ON DELETE CASCADE,
    scope_key varchar(128) NOT NULL,
    location_label varchar(256) NOT NULL,
    listener_count bigint NOT NULL,
    PRIMARY KEY (ranked_hour, scope_key)
);
CREATE TABLE ranking.hourly_track_rank (
    ranked_hour timestamptz NOT NULL,
    scope_key varchar(128) NOT NULL,
    track_id varchar(128) NOT NULL REFERENCES ranking.track(track_id),
    rank integer NOT NULL CHECK (rank BETWEEN 1 AND 20),
    unique_listener_count bigint NOT NULL CHECK (unique_listener_count >= 3),
    completion_count bigint NOT NULL,
    last_completed_at timestamptz NOT NULL,
    PRIMARY KEY (ranked_hour, scope_key, track_id),
    UNIQUE (ranked_hour, scope_key, rank),
    FOREIGN KEY (ranked_hour, scope_key) REFERENCES ranking.hourly_chart(ranked_hour, scope_key) ON DELETE CASCADE
);
CREATE TABLE ranking.batch_attempt (
    attempt_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ranked_hour timestamptz NOT NULL,
    finished_at timestamptz NOT NULL,
    status varchar(16) NOT NULL,
    duration_ms bigint NOT NULL,
    error_type varchar(256)
);
