# 통근길 플레이리스트 — 프로젝트 통합 문서

> 팀 노션에 흩어진 주제/아키텍처/ERD 내용을 한 곳에 모은 통합 레퍼런스입니다.
> GitHub 저장소 세팅 시 컨텍스트로 사용하기 위해 작성되었습니다.
> 기능명세/API명세는 계속 갱신 중이라 이 문서에 포함하지 않음 — Notion 참고.

---

## 1. 프로젝트 개요

**한 줄 정의**: 통근 정보(노선/소요시간)에 맞춰 음악을 자동 추천해주고, 홈 화면에 연령대 기반 배너 광고를 노출하는 음악+광고 통합 플랫폼.

**팀 구성**: 5명 (광고 시스템 2명, 음악 앱 2명, 나머지 1명 공통 작업 겸임)

**일정**
- Phase 1: ~10/7
- Phase 2: ~10/16
- Phase 3(고도화): 이후, 시간 남는 만큼 진행
- 코어타임: 매주 목·토 오전 10시

---

## 2. 시스템 아키텍처

### 2-A. 배포 구조

```
[GitHub] → [GitHub Actions] → 빌드 후 배포
    ├─ 프론트: S3(정적 파일) + CloudFront(CDN)  ※ Vercel 대체, 실무 경험 목적
    │     GitHub Actions 단계: npm run build → aws s3 sync ./dist s3://버킷 --delete
    │                          → aws cloudfront create-invalidation --paths "/*"
    └─ 백엔드: EC2 (Docker Compose)

[EC2 메인 서버]
├── NGINX (리버스 프록시)
├── Spring Boot (API 서버 — 계정/캠페인/배너/클릭 전부 하나의 애플리케이션, 모놀리식+역할분리, MSA 아님)
├── 클릭 처리 서비스 (P3, Spring, 같은 코드를 별도 실행) → Redis 중복 확인 → Kafka 직접 전송
├── PostgreSQL (P1~)
├── Redis (P3, 2주차~: 상위 N·후보 캐시, 클릭 중복 확인 `notification_id`)
├── Kafka (P3, click-events 토픽만)
└── Prometheus + Grafana (P3, 모니터링)

[별도 EC2 또는 같은 서버 내 분리 — DB 계층]
├── PostgreSQL
└── ClickHouse (P3, OLAP — Kafka Engine + Materialized View)

[Amazon S3] — 원본 클릭 로그 적재 (P3, 재집계 서비스가 Kafka 를 읽어 직접 저장)
```

**⚠️ 아직 미정**: GitHub 저장소를 모노레포(프론트+백엔드 한 저장소)로 할지, 분리할지 팀 결정 필요.

### 2-B. 프론트엔드 스택
React + Vite + Zustand + Tailwind + TanStack Query

### 2-C. 외부 API 연동

| 용도 | API | 상태/주의사항 |
|---|---|---|
| 음악 검색/재생 | **iTunes Search API** (Deezer에서 전환 중) | 인증 불필요, CORS 미지원(백엔드 프록시 필수), 30초 미리듣기만 제공(프로모션 목적 ToS 유의). `country=KR`은 `entity=song`/`album`과 같이 쓰면 결과가 0건이 되는 버그가 있어 쓰지 않는다(#47) |
| 날씨 | **OpenWeatherMap** | 무료 티어(분당 60회/월 100만 회)로 충분 |
| 지도/장소 검색 | **카카오맵 API** (JS SDK + 로컬 API) | 무료 할당량 충분(일 10만 건). **주의: 검색 결과를 DB에 대량 캐싱/재사용하는 것은 카카오 정책상 금지.** 유저가 선택한 좌표 1개를 프로필에 저장하는 정도는 문제 없음 |
| ~~음악 재생(초기안)~~ | ~~Spotify~~ | **제외됨** — 2026년 2월 정책 변경(개발자 Premium 필수 + 테스트 유저 5명 제한)으로 데모 불가능 |
| ~~음악 재생(2차안)~~ | ~~Deezer~~ | **재검토 중** — 소비자 서비스 한국 미제공 이슈로 iTunes로 전환 |

### 2-D. 설계 원칙 (왜 이렇게 구성했는지)

- **Kafka/Redis/ClickHouse/S3는 전부 Phase 3부터 도입.** 처음부터 넣지 않고 PostgreSQL 단일 구조(P1) 시작 → 부하 테스트 실측 → 단계적 도입.
- **Kafka 도입 근거(계산)**: 가상 유저 10만 명, 동시 캠페인 200개, 캠페인당 평균 750명 타겟, 클릭률 25% 가정 시 피크 시 초당 약 1,125건 클릭이 동일 `campaign_stats` 행에 집중 → PostgreSQL 단일 행 UPDATE 한계(초당 수백 건) 근접/초과 예상.
- **OLAP(ClickHouse) 도입 근거**: 멘토 피드백 — "Kafka 원본 테이블에 직접 SELECT하면 응답속도 문제, Roll-up + Materialized View로 미리 집계된 결과 테이블을 따로 둬야 함." → `click_events_kafka`(통로) → MV → `click_facts`(SummingMergeTree, 실제 저장) 구조로 해결.
- **S3 원시 데이터 보관 이유**: 실시간 집계는 빠르지만 드물게 오차 가능 → 원본을 S3에 그대로 보관, 재집계 서비스가 주기적으로 재계산해 대조/보정.
- **노출(impression)과 클릭(click) 분리 기록**: CTR = 클릭수 ÷ 노출수 계산을 위해 `ad_impressions` 테이블 별도 존재.
- **클릭 전송 방식 (2026-10-30 결정)**: 클릭 처리 서비스가 Redis 로 중복을 확인한 뒤 Kafka 로 바로 보낸다. PostgreSQL 중간 표(아웃박스)와 릴레이는 쓰지 않는다. 클릭 원본 쓰기는 하루 비교 뒤 PostgreSQL 에서 끈다.
- **조정 (2026-10-30 기준)**: 재집계 서비스가 Kafka 에서 읽은 수 = S3 에 쓴 줄 수, S3 고유 `notification_id` 수 = ClickHouse `click_facts` 일별 합계 (재계산 후). 차이 0, 차이는 경고만.
- **MSA 아님**: "이벤트 기반 워커 분리" 구조. 서비스들이 PostgreSQL/Redis를 공유하고 REST가 아닌 Kafka 큐로만 연결됨.

---

## 3. ERD

### 3-A. PostgreSQL (Phase 1 기준, 광고 시스템)

```sql
CREATE TABLE advertisers (  -- 광고주 (음악 앱 일반 유저의 users 와 구분하려고 advertisers 로 명명)
    user_id BIGINT PRIMARY KEY,
    email VARCHAR(128),
    password_hash VARCHAR(256),
    business_name VARCHAR(128),
    plan VARCHAR(16),
    refresh_token_hash VARCHAR(64),  -- 로그인 때 발급한 refresh token 의 SHA-256 해시(원문 아님). 로그아웃하면 NULL, 광고주당 1개 세션
    created_at TIMESTAMP, updated_at TIMESTAMP, deleted_at TIMESTAMP
);

CREATE TABLE campaigns (  -- 캠페인 (위치 컬럼 삭제됨, 연령대 타겟으로 변경)
    campaign_id BIGINT PRIMARY KEY,
    user_id BIGINT REFERENCES advertisers(user_id),
    title VARCHAR(128),
    body TEXT,
    image_url VARCHAR(512),
    link_url VARCHAR(512),
    target_age_group VARCHAR(16),   -- 신규: 연령대 매칭용 (기존 center_lat/lng/radius_m 대체)
    time_start TIMESTAMP,
    time_end TIMESTAMP,
    weather_condition VARCHAR(32),  -- Phase 3, 맥락 조건
    status VARCHAR(16),
    created_at TIMESTAMP, updated_at TIMESTAMP, deleted_at TIMESTAMP
);

CREATE TABLE target_users (  -- 타겟(가상) 유저
    target_user_id BIGINT PRIMARY KEY,
    age_group VARCHAR(16),
    gender VARCHAR(8),
    created_at TIMESTAMP, updated_at TIMESTAMP, deleted_at TIMESTAMP
);

CREATE TABLE ad_impressions (  -- 신규: 노출 기록 (클릭률 계산용)
    notification_id VARCHAR(36) PRIMARY KEY,
    campaign_id BIGINT REFERENCES campaigns(campaign_id),
    target_user_id BIGINT REFERENCES target_users(target_user_id),
    shown_at TIMESTAMP,
    created_at TIMESTAMP, updated_at TIMESTAMP, deleted_at TIMESTAMP
);

CREATE TABLE click_events (  -- 클릭 원본 (2026-10-30: 신규 쓰기 중단, 기존 데이터만 보관. 중복은 Redis 확인)
    click_id BIGINT PRIMARY KEY,
    notification_id VARCHAR(36) UNIQUE REFERENCES ad_impressions(notification_id),
    campaign_id BIGINT,
    target_user_id BIGINT,
    clicked_at TIMESTAMP,
    created_at TIMESTAMP, updated_at TIMESTAMP, deleted_at TIMESTAMP
);
-- 관계: ad_impressions(1) : click_events(0..1)  ※ 1:N 아님. 중복 클릭은 Redis 확인으로 막음 (2026-10-30)

CREATE TABLE campaign_stats (  -- Phase 1 한정, Phase 3부터 ClickHouse로 역할 이전. 클릭 열은 하루 비교 뒤 갱신 중단 (2026-10-30)
    campaign_id BIGINT PRIMARY KEY REFERENCES campaigns(campaign_id),
    sent_count BIGINT,
    click_count BIGINT,
    created_at TIMESTAMP, updated_at TIMESTAMP, deleted_at TIMESTAMP
);

CREATE TABLE gps_log (  -- 실시간 랭킹(주변 인기곡) 기능용. 음악 재생 시 GPS 좌표 원본 수집
    gps_log_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION,
    recorded_at TIMESTAMP
);
```

**GPS 재도입**: `user_locations` 테이블은 삭제됐지만, 이후 실시간 랭킹(주변 인기곡) 기능에서 `gps_log` 테이블로 GPS 수집이 다시 도입됐다. 음악 재생 시점에 원본 좌표를 `gps_log`에 그대로 기록하고, "근처" 판별을 위한 셀 단위 집계는 `popular_track_ranking.geo_cell` 컬럼에서 처리한다.

### 3-B. PostgreSQL (음악 앱 도메인)

```sql
CREATE TABLE users (  -- 음악 앱 일반 유저 (광고주 계정인 advertisers 와 별개)
    user_id BIGINT PRIMARY KEY,
    email VARCHAR(255),
    password_hash VARCHAR(255),
    nickname VARCHAR(50),
    created_at TIMESTAMP, updated_at TIMESTAMP, deleted_at TIMESTAMP
);

CREATE TABLE commute_profile (  -- 통근 프로필(출발/도착지, 통근 구분). 한 유저가 여러 개 등록 가능
    commute_profile_id BIGINT PRIMARY KEY,
    user_id BIGINT REFERENCES users(user_id),
    origin_name VARCHAR(255),
    origin_lat DOUBLE PRECISION,
    origin_lng DOUBLE PRECISION,
    destination_name VARCHAR(255),
    destination_lat DOUBLE PRECISION,
    destination_lng DOUBLE PRECISION,
    commute_type VARCHAR(20),  -- 출근/퇴근/기타
    created_at TIMESTAMP
);

CREATE TABLE commute_route (  -- 프로필 1개에 교통수단별 경로가 여러 개 달릴 수 있음(1:N)
    route_id BIGINT PRIMARY KEY,
    commute_profile_id BIGINT REFERENCES commute_profile(commute_profile_id),
    transport_type VARCHAR(20),  -- 대중교통/도보/자차. TMAP이 지하철/버스를 구분 계산해주지 않아 대중교통으로 통합(#85)
    avg_duration_min INT,  -- TMAP 기반 자동계산
    route_detail JSON,  -- 지도API 원본 응답 캐싱용 (컬럼만 존재, 아직 미구현)
    created_at TIMESTAMP
);

CREATE TABLE playlist (  -- 사용자 직접 생성(USER_CREATED) 또는 서버 추천 생성(COMMUTE_GENERATED)
    playlist_id BIGINT PRIMARY KEY,
    user_id BIGINT REFERENCES users(user_id),
    title VARCHAR(100),
    description VARCHAR(500),
    is_public BOOLEAN DEFAULT TRUE,
    total_duration_sec INT,
    type VARCHAR(20) NOT NULL DEFAULT 'USER_CREATED',  -- USER_CREATED | COMMUTE_GENERATED
    share_token VARCHAR(36) UNIQUE,  -- 공유 링크 토큰. 공유 중일 때만 값이 있다
    created_at TIMESTAMP, updated_at TIMESTAMP, deleted_at TIMESTAMP
);

CREATE TABLE track (  -- 곡 자체 정보. 여러 플레이리스트에서 재사용되는 공용 테이블
    track_id BIGINT PRIMARY KEY,
    itunes_track_id VARCHAR(100) UNIQUE,
    title VARCHAR(200),
    artist VARCHAR(200),
    genre VARCHAR(50),  -- 날씨/시간대 기반 추천에 사용
    collection_name VARCHAR(200),
    artwork_url VARCHAR(500),
    preview_url VARCHAR(500),
    track_time_millis INT,
    created_at TIMESTAMP, updated_at TIMESTAMP, deleted_at TIMESTAMP
);

CREATE TABLE track_mood (  -- 곡의 분위기 점수. 한 곡에 여러 분위기가 붙는다 (날씨·시간대 추천의 후보 선정에 사용)
    track_mood_id BIGINT PRIMARY KEY,
    track_id BIGINT NOT NULL REFERENCES track(track_id),
    mood VARCHAR(20) NOT NULL,  -- GLOOMY | CALM | UPBEAT | ENERGETIC | FOCUS | ROMANTIC | NOSTALGIC | DREAMY
    score DOUBLE PRECISION NOT NULL,  -- 0~1, 클수록 그 분위기에 잘 맞음
    source VARCHAR(20) NOT NULL,  -- KEYWORD(수집할 때 검색어만 반영) | SCORED(여러 신호를 합산한 최종 값)
    created_at TIMESTAMP, updated_at TIMESTAMP, deleted_at TIMESTAMP,
    UNIQUE (track_id, mood)
);

CREATE TABLE playlist_track (  -- 플레이리스트-곡 연결 테이블(N:M)
    playlist_track_id BIGINT PRIMARY KEY,
    playlist_id BIGINT REFERENCES playlist(playlist_id),
    track_id BIGINT REFERENCES track(track_id),
    created_at TIMESTAMP, updated_at TIMESTAMP, deleted_at TIMESTAMP
);

CREATE TABLE track_like (  -- 곡 좋아요 (Phase 3)
    track_id BIGINT REFERENCES track(track_id),
    user_id BIGINT REFERENCES users(user_id),
    created_at TIMESTAMP
);

CREATE TABLE follow (  -- 사용자 팔로우 (Phase 3)
    follower_id BIGINT REFERENCES users(user_id),
    followee_id BIGINT REFERENCES users(user_id),
    created_at TIMESTAMP
);

CREATE TABLE now_playing (  -- 현재 재생 중인 곡 (공유/조회 기능용)
    user_id BIGINT PRIMARY KEY REFERENCES users(user_id),
    playlist_id BIGINT REFERENCES playlist(playlist_id),
    track_id BIGINT REFERENCES track(track_id),
    is_public BOOLEAN DEFAULT TRUE,
    updated_at TIMESTAMP
);

CREATE TABLE playlist_active_listening (  -- 공유 플레이리스트를 지금 듣는 회원과 위치 셀. 회원당 1행, 재생 신호가 끊기면 정리 작업이 삭제
    member_id BIGINT PRIMARY KEY,
    playlist_id BIGINT NOT NULL,
    h3_cell VARCHAR(15) NOT NULL,  -- H3 해상도 8
    location_recorded_at TIMESTAMPTZ NOT NULL,
    heartbeat_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE commute_session (  -- 통근 한 번의 기록. 회원당 진행 중(ACTIVE)인 통근은 1개
    commute_session_id BIGINT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    commute_profile_id BIGINT NOT NULL,
    playlist_id BIGINT,
    status VARCHAR(20) NOT NULL,  -- ACTIVE | COMPLETED | CANCELED
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    expected_duration_minutes INT,  -- 시작할 때 프로필 경로에서 가져온 예상 소요시간
    active_member_id BIGINT UNIQUE,  -- 진행 중일 때만 member_id, 끝나면 NULL. 회원당 활성 1개를 DB 가 보장
    created_at TIMESTAMP, updated_at TIMESTAMP, deleted_at TIMESTAMP
);

CREATE TABLE commute_session_event (  -- 통근 중 재생 이벤트. event_id 로 중복 방지
    commute_session_event_id BIGINT PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    session_id BIGINT NOT NULL,
    itunes_track_id BIGINT NOT NULL,
    action_type VARCHAR(10) NOT NULL,  -- PLAY | SKIP | REPEAT
    played_seconds INT,
    created_at TIMESTAMP, updated_at TIMESTAMP, deleted_at TIMESTAMP
);

CREATE TABLE playback_event (  -- 재생 이벤트(PLAY/SKIP/REPEAT). 주변 인기곡 집계(popular_track_ranking)의 원본
    playback_event_id BIGINT PRIMARY KEY,
    user_id BIGINT REFERENCES users(user_id),
    track_id BIGINT REFERENCES track(track_id),
    action_type VARCHAR(20),  -- PLAY/SKIP/REPEAT
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    played_at TIMESTAMP
);

CREATE TABLE popular_track_ranking (  -- 주변 인기곡 집계 결과. geo_cell 단위로 play_count 롤업
    rank_no SMALLINT,
    ranking_id BIGINT,
    track_id BIGINT REFERENCES track(track_id),
    geo_cell VARCHAR(20),  -- 위치 집계 단위
    play_count INT,
    aggregated_at TIMESTAMP,
    PRIMARY KEY (rank_no, ranking_id)
);
```

**빠진 테이블**: `recommendation`은 ERD에서 SQL 자체가 깨져서 export됐고(FK 선언이 COMMENT 문자열 안에 섞여 들어감) 실제 사용 여부도 불확실해 제외.

### 3-C. ClickHouse (Phase 3, 별도 다이어그램/DB)

```sql
-- Kafka 에서 읽는 통로 (저장 안 함). 파티션 3 이므로 소비자도 최대 3
CREATE TABLE click_events_kafka (
    notification_id String,
    campaign_id     Int64,
    age_group       String,
    gender          String,
    clicked_at      DateTime
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'click-events',
         kafka_group_name = 'clickhouse-group',
         kafka_format = 'JSONEachRow',
         kafka_num_consumers = 3;

-- click_raw 와 click_raw_mv 는 만들지 않는다. 중복은 Redis 확인과 재계산에서 처리한다.

-- 대시보드가 읽는 집계 (분·연령대·성별 합계)
CREATE TABLE click_facts (
    campaign_id  Int64,
    minute       DateTime,
    age_group    LowCardinality(String),
    gender       LowCardinality(String),
    click_count  UInt64
) ENGINE = SummingMergeTree
PARTITION BY toYYYYMMDD(minute)
ORDER BY (campaign_id, minute, age_group, gender);

-- 재계산용 스테이징 (같은 구조)
CREATE TABLE click_facts_stage AS click_facts;

CREATE MATERIALIZED VIEW click_facts_mv TO click_facts AS
SELECT campaign_id, toStartOfMinute(clicked_at) AS minute, age_group, gender, count() AS click_count
FROM click_events_kafka
GROUP BY campaign_id, minute, age_group, gender;
```

### 3-D. S3 (테이블 아님, 파일 저장 규칙)

```
버킷: raw-click-data
경로: raw-clicks/{yyyy}/{MM}/{dd}/{HH}/{배치ID}.json   (날짜는 clicked_at 기준)
형식: JSON Lines (한 줄 = 클릭 1건)
필드: notification_id, campaign_id, age_group, gender, clicked_at
파일 닫기: 1분 경과 또는 1,000건 중 먼저 오는 것
저장 주체: 재집계 서비스가 Kafka 를 읽어 직접 씀 (2026-10-30)
```

---

## 4. CI/CD 요구사항 (GitHub 세팅 시 참고)

**필요한 워크플로우**
1. **PR 생성 시**: 빌드 + 테스트 실행, **CodeRabbit** 자동 코드 리뷰 연동(GitHub Marketplace에서 앱 설치 필요, 저장소 Settings에서 권한 부여)
2. **main 브랜치 merge 시(배포)**:
   - 프론트: `npm run build` → S3 버킷 sync → CloudFront 캐시 무효화(invalidation)
   - 백엔드: Docker 이미지 빌드 → EC2로 배포(SSH 접속 후 `docker-compose up -d --build` 또는 이미지 pull 방식)
3. **브랜치 전략**: `main`(배포용) / `develop`(통합) / `feature/*`(기능별) 권장 — 팀 확정 필요

**필요한 Secrets (GitHub Actions)**
- AWS 자격증명 (S3/CloudFront 배포용)
- EC2 SSH 접근 키
- 외부 API 키 (OpenWeatherMap, 카카오맵) — iTunes Search API는 키 불필요

---

## 5. 참고한 외부 레퍼런스

- Hello Interview - Ad Click Aggregator (Flink/Kinesis/OLAP 기반 원문 설계 — 규모에 맞게 경량화, 원시/집계 분리·비동기 처리·멱등성·정합성 재검증 원칙만 차용)
- 우아한형제들 기술 블로그 - "대규모 시스템 설계 기초 2권: 광고 클릭 이벤트 집계" (원시DB/집계DB 분리, 비동기 처리, Druid 롤업 — Druid/맵리듀스는 규모상 제외)
- AWS 블로그 - Geo-based Real-time Marketing (맥락 데이터 타겟팅 참고용)
- 벨로그 - "가상 면접 사례로 배우는 대규모 시스템 설계 기초 2: 광고 클릭 이벤트 집계" (Kafka 비동기 전송, Redis 노출 ID 중복 확인, 스트림 먼저 → 캐시 나중)
- HelloInterview - Ad Click Aggregator 의 Redis 중복 캐시와 S3 아카이브 방식 (2026-10-30 결정 근거)
