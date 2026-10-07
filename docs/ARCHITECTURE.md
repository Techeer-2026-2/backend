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
├── PostgreSQL (P1~)
├── Redis (현재 용도 보류 — 위치 삭제 + 알림 보류로 실질 사용처 없음)
├── Kafka (P3, click-events 토픽만)
└── Prometheus + Grafana (P3, 모니터링)

[별도 EC2 또는 같은 서버 내 분리 — DB 계층]
├── PostgreSQL
└── ClickHouse (P3, OLAP — Kafka Engine + Materialized View)

[Amazon S3] — 원본 클릭 로그 적재 (P3)
```

**⚠️ 아직 미정**: GitHub 저장소를 모노레포(프론트+백엔드 한 저장소)로 할지, 분리할지 팀 결정 필요.

### 2-B. 프론트엔드 스택
React + Vite + Zustand + Tailwind + TanStack Query

### 2-C. 외부 API 연동

| 용도 | API | 상태/주의사항 |
|---|---|---|
| 음악 검색/재생 | **iTunes Search API** (Deezer에서 전환 중) | 인증 불필요, `country=KR` 파라미터 지원, CORS 미지원(백엔드 프록시 필수), 30초 미리듣기만 제공(프로모션 목적 ToS 유의) |
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

CREATE TABLE click_events (  -- 클릭 원본 (notification_id UNIQUE로 멱등성 보장)
    click_id BIGINT PRIMARY KEY,
    notification_id VARCHAR(36) UNIQUE REFERENCES ad_impressions(notification_id),
    campaign_id BIGINT,
    target_user_id BIGINT,
    clicked_at TIMESTAMP,
    created_at TIMESTAMP, updated_at TIMESTAMP, deleted_at TIMESTAMP
);
-- 관계: ad_impressions(1) : click_events(0..1)  ※ 1:N 아님, 멱등성으로 중복 클릭 방지

CREATE TABLE campaign_stats (  -- Phase 1 한정, Phase 3부터 ClickHouse로 역할 이전
    campaign_id BIGINT PRIMARY KEY REFERENCES campaigns(campaign_id),
    sent_count BIGINT,
    click_count BIGINT,
    created_at TIMESTAMP, updated_at TIMESTAMP, deleted_at TIMESTAMP
);
```

**삭제된 테이블**: `user_locations` (GPS/위치 개념 폐기로 통째로 삭제)

### 3-B. ClickHouse (Phase 3, 별도 다이어그램/DB)

```sql
CREATE TABLE click_events_kafka (  -- 통로 역할, 저장 안 됨
    campaign_id BIGINT,
    notification_id String,
    clicked_at DateTime
) ENGINE = Kafka
SETTINGS kafka_broker_list = 'kafka:9092',
         kafka_topic_list = 'click-events',
         kafka_group_name = 'clickhouse-group',
         kafka_format = 'JSONEachRow';

CREATE TABLE click_facts (  -- 실제 저장되는 롤업 결과
    campaign_id BIGINT,
    minute DateTime,
    click_count UInt32
) ENGINE = SummingMergeTree()
ORDER BY (campaign_id, minute);

CREATE MATERIALIZED VIEW click_facts_mv TO click_facts AS
SELECT campaign_id, toStartOfMinute(clicked_at) AS minute, count() AS click_count
FROM click_events_kafka
GROUP BY campaign_id, minute;
```

### 3-C. S3 (테이블 아님, 파일 저장 규칙)

```
버킷: raw-click-data
경로: raw-clicks/{year}/{month}/{day}/{hour}/*.json
형식: JSON Lines (한 줄 = 클릭 이벤트 1건)
필드: notification_id, campaign_id, clicked_at
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
