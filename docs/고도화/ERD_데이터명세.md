# ERD·데이터 명세 (정본)

> 이 문서가 **ERD 와 데이터 형식의 정본**이다. `고도화_계획.md` 의 6절(ERD 변경안), 11-2·11-3(변경 목록), 3-6·3-8(재계산·S3)은 이 문서를 가리킨다.
> 기준: backend `origin/develop` = `6d10534` (#111 머지 후), 고도화 계획 확정 결정 (2026-10-29).
> 표기: **[지금]** = PostgreSQL 1단계, **[Kafka]** = Kafka 연결 단계(3주차~), **[ClickHouse]** = 4주차, **[보류]** = 조건 충족 시, **[제안]** = 원문에 DDL 이 없어서 이 문서에서 새로 만든 것, **[확인 필요]** = 원문끼리 어긋나거나 실제 DB 확인이 필요한 것.

---

## 0. 한눈에 보기

| 저장소 | 추가 | 수정 | 유지 | 만들지 않음 |
|---|---|---|---|---|
| PostgreSQL | 6개 (`campaign_minute_stats`, `campaign_budget`, `campaign_slot_budget`, `refund_ledger`, `outbox_events`, `recompute_log`[확정]) | 3개 (`ad_impressions`, `click_events`, `campaigns`) | 광고 기존 표 전부 (`campaign_stats` 포함) | `top_ads_snapshot` [확인 필요], `impression_facts` (ClickHouse 노출 표) |
| ClickHouse | 6개 표 + 구체화 뷰 4개 (롤업 포함) | — | — | 노출용 표 |
| S3 | 파일 경로 규칙 1개 (`raw-clicks/`) | — | — | — |
| Redis | 키 3종 (DB 아님, 캐시) | — | — | — |

---

## 1. 현재 광고 관련 테이블 (develop 기준, 변경 전)

| 표 | 역할 | 주요 열 | 비고 |
|---|---|---|---|
| `advertisers` | 광고주 계정 | `user_id`(PK), 이메일, 비밀번호 해시, 사업자명, `plan`, `refresh_token_hash` | 음악 앱의 `users` 와 별개 |
| `campaigns` | 캠페인 | `campaign_id`(PK), `user_id`, `title`, `body`, `image_url`, `link_url`, `time_start`, `time_end`, `status`, `weather_condition`, `deleted_at` 외 BaseEntity 열 | soft delete |
| `campaign_age_groups` | 캠페인 ↔ 연령대 (1행 1값) | `campaign_id`, `age_group` | `@ElementCollection`. **PK 존재 여부 확인 필요** [확인 필요] |
| `campaign_genders` | 캠페인 ↔ 성별 (1행 1값) | `campaign_id`, `gender` | 같음 [확인 필요] |
| `campaign_stats` | 캠페인 누적 노출·클릭 (1행) | `campaign_id`(PK), `sent_count`, `click_count` | 대시보드 누적 숫자가 읽음 |
| `target_users` | 배너를 볼 가상 유저 | `target_user_id`(PK), `age_group`, `gender`(`M`/`F` 또는 `male`/`female`) | |
| `ad_impressions` | 노출 1건 | `notification_id`(PK, 36자), `campaign_id`, `target_user_id`, `shown_at` | |
| `click_events` | 클릭 1건 | `click_id`(PK), `notification_id`(UNIQUE), `campaign_id`, `target_user_id`, `clicked_at` | 중복 클릭은 `ON CONFLICT DO NOTHING` |

> 음악·랭킹·통근 관련 표(`users`, `playlists`, `tracks`, `ranking_*`, `commute_*` 등)는 이 명세 범위 밖이다. 같은 DB에 있어도 이번 고도화에서 건드리지 않는다.

---

## 2. PostgreSQL 변경

### 2-1. [지금] `ad_impressions`, `click_events` 에 연령대·성별 열 추가 (수정)

```sql
ALTER TABLE ad_impressions
    ADD COLUMN age_group VARCHAR(16),
    ADD COLUMN gender    VARCHAR(8);

ALTER TABLE click_events
    ADD COLUMN age_group VARCHAR(16),
    ADD COLUMN gender    VARCHAR(8);
```

- 값: `age_group` 은 `10s`~`50s`, `gender` 는 `male`/`female`.
- **노출 생성 시** `target_users` 의 값을 복사한다. `M`/`F` 로 저장된 옛 값은 `male`/`female` 로 바꾼다 (`BannerService.toGenderCode` 와 같은 규칙).
- **클릭 생성 시** 연결된 노출(`ad_impressions`)의 값을 그대로 가져온다. 유저 정보가 나중에 바뀌어도 기록은 남는다.
- 기존 행은 NULL 로 두고 **백필(3절)** 로 채운다.
- 근거: 질의 (3) 속성 필터 (`고도화_계획.md` 6-1 ①, 2-4). IP·국가는 수집하지 않는다.

### 2-2. [지금] `campaigns` 에 입찰가 열 추가 (수정)

```sql
ALTER TABLE campaigns ADD COLUMN bid_price INT NOT NULL DEFAULT 0;   -- 노출 1회당 금액(원)
```

- 근거: 광고 선택 (`고도화_계획.md` 4절, 6-1 ⑥).

### 2-3. [지금] `campaign_minute_stats` (분 버킷 표, 신규)

```sql
CREATE TABLE campaign_minute_stats (
    campaign_id    BIGINT       NOT NULL REFERENCES campaigns(campaign_id),
    minute_bucket  TIMESTAMP    NOT NULL,              -- 분 단위로 자른 시각 (초 = 0)
    age_group      VARCHAR(16)  NOT NULL,              -- 값이 없으면 'unknown'
    gender         VARCHAR(8)   NOT NULL,              -- 값이 없으면 'unknown'
    sent_count     BIGINT       NOT NULL DEFAULT 0,
    click_count    BIGINT       NOT NULL DEFAULT 0,
    updated_at     TIMESTAMP    NOT NULL,
    PRIMARY KEY (campaign_id, minute_bucket, age_group, gender)
);

CREATE INDEX idx_cms_minute ON campaign_minute_stats (minute_bucket);
```

- 행 1개 = 캠페인 · 분 · 연령대 · 성별 조합 1개. "전체" 행은 없고, 행을 합해서 구한다.
- 클릭 1건은 행 1개만 올린다 (쓰기 1번). 노출·클릭 요청 안에서 **같은 트랜잭션**으로 UPSERT.
- `idx_cms_minute` 이유: 상위 N 은 여러 캠페인에 걸쳐 시간 범위로 읽는다. PK 첫 칸이 캠페인이라 시간 범위 조회가 느리다.
- 근거: 질의 (1) 최근 M분 클릭 수, (2) 상위 N (`고도화_계획.md` 6-1 ②, 2-1).
- **확정 (2026-10-29): 연령대·성별을 모르면 `unknown` 으로 저장한다.** 노출·클릭 모두 같은 규칙. 기존 행의 NULL 도 백필 때 `unknown` 으로 채운다 (3절).

### 2-4. [지금] `campaign_stats` (유지, 변경 없음)

- 삭제하지 않는다. 대시보드 누적 숫자(SSE)가 계속 읽는다.
- Kafka 연결 후에는 요청 경로에서 `click_count` 갱신을 멈춘다 (`sent_count` 는 계속 갱신).
- 분 버킷 합계와 맞는지 조정 쿼리로 비교한다 (7절).

### 2-5. [지금] 인덱스 정정

```sql
CREATE INDEX idx_campaign_age_groups_age ON campaign_age_groups (age_group, campaign_id);
CREATE INDEX idx_campaign_genders_gender ON campaign_genders (gender, campaign_id);
CREATE INDEX idx_click_events_campaign_time ON click_events (campaign_id, clicked_at);
CREATE INDEX idx_impressions_campaign_time  ON ad_impressions (campaign_id, shown_at);
```

- 옛 계획의 `campaigns(target_age_group, ...)` 인덱스는 **열이 없어서 폐기**했다. 연령대·성별은 `campaign_age_groups`, `campaign_genders` 에 한 줄씩 있다.
- 배너 선택 질의(`findRandomActiveForTarget`)가 두 표를 `EXISTS` 로 읽으므로 이 인덱스가 필요하다.
- **[확인 필요]** 두 표의 기존 키 구성(PK 여부)을 생성 전에 확인한다. 효과는 `EXPLAIN (ANALYZE, BUFFERS)` 로 측정한다.

### 2-6. [지금] `campaign_budget`, `campaign_slot_budget`, `refund_ledger` (신규, 예산)

```sql
CREATE TABLE campaign_budget (
    campaign_id   BIGINT    PRIMARY KEY REFERENCES campaigns(campaign_id),
    total_budget  BIGINT    NOT NULL,              -- 총 예산(원)
    spent_total   BIGINT    NOT NULL DEFAULT 0,
    updated_at    TIMESTAMP NOT NULL,
    CHECK (spent_total <= total_budget)
);

CREATE TABLE campaign_slot_budget (
    campaign_id  BIGINT      NOT NULL REFERENCES campaigns(campaign_id),
    slot         VARCHAR(16) NOT NULL,              -- MORNING(06~09시) | EVENING(17~20시)
    slot_cap     BIGINT      NOT NULL,              -- 이 시간대에 쓸 수 있는 최대 금액 (총 예산 기준, 이월 없음)
    slot_spent   BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (campaign_id, slot),
    CHECK (slot_spent <= slot_cap)
);

CREATE TABLE refund_ledger (
    refund_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campaign_id  BIGINT    NOT NULL REFERENCES campaigns(campaign_id),
    remaining    BIGINT    NOT NULL,              -- 기간 종료 시 남은 예산 (기록만, 실제 환불은 결제 도입 후)
    recorded_at  TIMESTAMP NOT NULL
);
```

- 차감: 두 표를 **한 트랜잭션**에서 조건부 UPDATE 한다. 출퇴근 시간이 아니면 `campaign_slot_budget` 은 건드리지 않는다.
- 조건부 UPDATE 형태 (고도화 4-6):
  ```sql
  UPDATE campaign_budget
     SET spent_total = spent_total + :bid, updated_at = :now
   WHERE campaign_id = :id
     AND spent_total + :bid <= total_budget;
  -- 출근이면 campaign_slot_budget 도 같은 방식으로 (slot_spent + :bid <= slot_cap)
  -- 0행이 바뀌면 차감 실패 → 그 광고 탈락
  ```
- 시간대 몫은 **상한**이다 (확률이 아님). 매일 리셋되지 않고, 남은 몫은 이월되지 않는다.
- 기간 종료 후 남은 예산은 `refund_ledger` 에 기록만 한다.
- 근거: 광고 선택 결정 (`고도화_계획.md` 4절, 4-8).

### 2-7. [1주차 DDL, 3주차부터 사용] `outbox_events` (신규, 발행 누락 방지)

```sql
CREATE TABLE outbox_events (
    outbox_id       BIGSERIAL    PRIMARY KEY,
    event_type      VARCHAR(32)  NOT NULL,        -- 'CLICK' 등
    notification_id VARCHAR(36)  NOT NULL UNIQUE, -- 중복 발행 방지 키
    payload         JSONB        NOT NULL,        -- 아래 payload 형식 (5절)
    created_at      TIMESTAMP    NOT NULL,
    published_at    TIMESTAMP,                    -- NULL 이면 미처리
    attempts        INT          NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
```

- 클릭 저장과 **같은 트랜잭션**에서 INSERT. 릴레이(`OutboxRelay`)가 미발행 행을 읽어 Kafka 로 보내고 `published_at` 을 채운다.
- 릴레이 여러 개일 때는 `SKIP LOCKED` 로 같은 행을 한 번만 가져간다.
- 근거: 클릭 저장과 발행 사이의 누락 방지 (`고도화_계획.md` 6-1 ⑦, 2-2).

### 2-8. [확정] `recompute_log` (재계산·조정 실행 기록, 신규)

> **원문에 DDL 이 없었다.** 11-2 에 "추가"로만 있고, 3-8 에 기록할 값 목록만 있었다. 아래는 그 목록을 열로 만든 안이고, **2026-10-29 승인으로 확정했다.**

```sql
CREATE TABLE recompute_log (
    recompute_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    target_date      DATE         NOT NULL,        -- 재계산한 날짜 (파티션 단위)
    started_at       TIMESTAMP    NOT NULL,
    finished_at      TIMESTAMP,
    status           VARCHAR(16)  NOT NULL,        -- RUNNING | SUCCESS | FAILED
    files_read       INT,                          -- S3 에서 읽은 파일 수
    unique_clicks    BIGINT,                       -- notification_id 기준 고유 클릭 수 (S3)
    replaced_total   BIGINT,                       -- 교체 후 click_facts 합계
    pg_click_count   BIGINT,                       -- 조정: PostgreSQL click_events 행 수 (같은 날짜)
    s3_unique_count  BIGINT,                       -- 조정: S3 고유 notification_id 수
    ch_raw_count     BIGINT,                       -- 조정: ClickHouse click_raw 행 수
    reconcile_ok     BOOLEAN,                      -- 세 값이 모두 같은지 (허용 오차 0)
    error_message    TEXT,
    UNIQUE (target_date, started_at)
);
```

- 용도: 재계산 실행 이력, 조정 결과, 실패 원인 확인. 지표 `recompute_last_success_timestamp` 의 근거.
- 근거: 3-8 (기록할 값), 7-3 (조정), 3-10 (재계산 실패 기록).

### 2-9. 만들지 않는 것 / 확인 필요

- **`impression_facts`** (ClickHouse 노출 표): **만들지 않음.** 노출 수는 PostgreSQL `campaign_stats.sent_count` 와 `campaign_minute_stats.sent_count` 에 계속 쌓는다. (결정 3-3-3)
- **`top_ads_snapshot`**: **만들지 않음 (확정, 2026-10-29).**
  - `고도화_계획.md` 6-1 ⑧ 과 11-2: **만들지 않음** (메모리 상태가 없어서 스냅숏 불필요).
  - `고도화_계획.md` 3-3 ("책의 스냅숏 개념 적용") 과 3-6 ("Redis 상위 N 키는 분마다 결과를 `top_ads_snapshot` 에 남긴다"): **만든다**.
  - 결정: **만들지 않는다.** 원문 3-3, 3-6 의 해당 문장은 이 결정에 맞춰 고친다 (계획 문서에 반영함). 나중에 Redis 복구 문제가 실제로 생기면 그때 추가한다.
- **`click_facts_hour`, `click_facts_day`** (롤업): **[계획 포함, 4주차 — 2026-10-29 정정].** 통계 API 는 지금 24시간만 허용하지만, 기간을 늘릴 때 바로 쓰도록 미리 계획에 넣었다. 만들 때 ClickHouse 구체화 뷰로 만들고 재계산 때 같이 교체한다 (3-11 원문 참고).

---

## 3. 백필 (과거 데이터 채우기)

고도화 6-5 와 `고도화_계획.md` D2 를 합친 절차다.

1. `ad_impressions`, `click_events` 의 `age_group`, `gender` 를 `target_users` 와 조인해서 채운다.
   - 값 규칙: `M` → `male`, `F` → `female`, 값이 없으면 `unknown` (확정, 2-1 참고).
2. `campaign_minute_stats` 의 과거 값은 `click_events.clicked_at`, `ad_impressions.shown_at` 으로 만든다.
3. 백필 SQL 은 **로컬 PostgreSQL 16 에서 먼저 검증**한다.
4. 검증 쿼리: 백필 이후 기간에 분 버킷 합계 = `campaign_stats` 증가분 (또는 `click_events` 행 수).

---

## 4. PostgreSQL 마이그레이션 순서 (운영)

원문 6-5 와 11절 기준. **운영 DB 는 사용자(민서님)만 접근한다.**

1. 열 추가 (NULL 허용, 기존 기능 영향 없음): 2-1, 2-2
2. 새 표 생성: 2-3, 2-6, (2-7 은 Kafka 단계), (2-8)
3. 인덱스 생성: 2-5 (기존 키 확인 후)
4. 백필: 3절
5. **구버전 서버 중지 → SQL 적용 → 새 버전 시작** (PR #111 때와 같은 순서)
6. 확인: 백필 이후 기간의 분 버킷 합계 ↔ `campaign_stats` 증가분

**Flyway 주의:** `application.yml` 의 Flyway 는 `schemas: ranking`, `locations: db/ranking` 전용이다. 광고 표는 Flyway 가 관리하지 않으므로 `db/stats/V1__*.sql` 을 만들어도 **자동 적용되지 않는다.** **방식 확정 (2026-10-29): 광고 SQL 은 수동으로 적용한다.** Flyway 에 광고 위치를 추가하지 않는다.

---

## 5. ClickHouse 구조 (4주차, Kafka 연결 뒤)

원문 6-2 DDL 그대로다.

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

-- 원본 (중복 제거). 같은 notification_id 는 한 줄로 합쳐짐
CREATE TABLE click_raw (
    notification_id String,
    campaign_id     Int64,
    age_group       LowCardinality(String),
    gender          LowCardinality(String),
    clicked_at      DateTime,
    ingested_at     DateTime DEFAULT now()   -- ClickHouse 가 받은 시각 (지연 측정용)
) ENGINE = ReplacingMergeTree
ORDER BY notification_id;

CREATE MATERIALIZED VIEW click_raw_mv TO click_raw AS
SELECT notification_id, campaign_id, age_group, gender, clicked_at
FROM click_events_kafka;

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

- **구체화 뷰는 2개** (`click_raw_mv`, `click_facts_mv`). 재계산은 뷰가 아니라 표 교체로 처리한다.
- `click_facts_mv` 는 Kafka 메시지가 두 번 오면 두 번 더한다 → **실시간 값은 잠정**. 확정은 재계산이 한다.
- `click_raw` 의 `ReplacingMergeTree` 는 **병합 때** 중복을 없앤다. 병합 전에는 같은 줄이 남아 있을 수 있다. 그래서 재계산은 `GROUP BY notification_id` 로 직접 중복을 없앤다. **[확인 필요]** 조정 쿼리에서 `click_raw` 를 셀 때 `FINAL` 을 붙일지 결정한다.
- **재계산 SQL** (3-8, 파티션 교체):
  ```sql
  INSERT INTO click_facts_stage
  SELECT campaign_id, toStartOfMinute(clicked_at) AS minute, age_group, gender, count() AS click_count
  FROM (
      SELECT notification_id,
             any(campaign_id) AS campaign_id,
             any(age_group)   AS age_group,
             any(gender)      AS gender,
             any(clicked_at)  AS clicked_at
      FROM s3('https://<bucket>.s3.ap-northeast-2.amazonaws.com/raw-clicks/2026/11/01/*/*.json',
              <access_key>, <secret_key>, 'JSONEachRow',
              'notification_id String, campaign_id Int64, age_group String, gender String, clicked_at DateTime')
      GROUP BY notification_id          -- 같은 클릭은 한 번만 센다
  )
  GROUP BY campaign_id, minute, age_group, gender;

  ALTER TABLE click_facts REPLACE PARTITION 20261101 FROM click_facts_stage;
  ```
- 롤업 표(`click_facts_hour`, `click_facts_day`)는 **계획에 포함 (4주차, 2026-10-29 정정)**. 기간 제한(24시간)을 늘리면 바로 쓴다. 만들 때 재계산 교체 대상에 함께 넣는다.

```sql
-- 시간 표 (분 표를 시간 단위로 합침, 날짜 단위로 파티션)
CREATE TABLE click_facts_hour (
    campaign_id  Int64,
    hour         DateTime,
    age_group    LowCardinality(String),
    gender       LowCardinality(String),
    click_count  UInt64
) ENGINE = SummingMergeTree
PARTITION BY toYYYYMMDD(hour)
ORDER BY (campaign_id, hour, age_group, gender);

CREATE MATERIALIZED VIEW click_facts_hour_mv TO click_facts_hour AS
SELECT campaign_id, toStartOfHour(minute) AS hour, age_group, gender, sum(click_count) AS click_count
FROM click_facts
GROUP BY campaign_id, hour, age_group, gender;

-- 일 표 (날짜 단위, 파티션도 하루 단위로 맞춰서 날짜별 교체가 가능하게 함)
CREATE TABLE click_facts_day (
    campaign_id  Int64,
    day          Date,
    age_group    LowCardinality(String),
    gender       LowCardinality(String),
    click_count  UInt64
) ENGINE = SummingMergeTree
PARTITION BY toYYYYMMDD(day)
ORDER BY (campaign_id, day, age_group, gender);

CREATE MATERIALIZED VIEW click_facts_day_mv TO click_facts_day AS
SELECT campaign_id, toDate(minute) AS day, age_group, gender, sum(click_count) AS click_count
FROM click_facts
GROUP BY campaign_id, day, age_group, gender;
```

- 주의: `REPLACE PARTITION` 은 MV 를 다시 실행하지 않는다. 그래서 재계산 때 `click_facts` 뿐 아니라 같은 날짜의 `click_facts_hour`, `click_facts_day` 도 **같이 교체**해야 한다 (계획 3-11).


---

## 6. S3 원본 (파일 형식 명세)

> 이 절이 원래 여러 곳에 흩어져 있던 것을 모았다. **[확인 필요]** 아래 표의 키 이름 표기가 원문에서 다르다 (6-3 참고).

### 6-1. 경로 규칙

```
s3://<버킷>/raw-clicks/{yyyy}/{MM}/{dd}/{HH}/{배치ID}.json
```

- 날짜·시간 폴더는 **클릭 시각(`clicked_at`)** 기준이다 (처리 시각 아님).
- 한 파일은 **JSON Lines** (한 줄에 한 클릭, 줄 끝 `\n`).
- 파일을 닫는 기준: **1분 경과 또는 1,000건** 중 먼저 오는 것.
- 배치ID: 파일마다 고유 (예: UUID 또는 시각 + 순번).
- 접근: EC2 의 IAM 역할을 먼저 쓰고, 안 되면 환경변수 키. `.env.example` 에는 **이름만** 적는다.

### 6-2. 한 줄의 필드

| 필드 | 타입 | 예시 | 설명 |
|---|---|---|---|
| `notification_id` | String (36자) | `3f2a...` | 노출 1건의 ID, 중복 제거 키 |
| `campaign_id` | Int64 | `12` | 캠페인 |
| `age_group` | String | `20s` | `10s`~`50s`, 없으면 `unknown` |
| `gender` | String | `female` | `male`/`female`, 없으면 `unknown` |
| `clicked_at` | DateTime (`YYYY-MM-DD hh:mm:ss`) | `2026-11-01 07:30:12` | 서버가 클릭 요청을 받은 시각 |

예시 한 줄:
```json
{"notification_id":"3f2a1c9e-...","campaign_id":12,"age_group":"20s","gender":"female","clicked_at":"2026-11-01 07:30:12"}
```

### 6-3. 키 이름 (확정: snake_case)

- `고도화_계획.md` 3-8 의 **메시지 형식 고정** 목록: `notificationId`, `campaignId`, `ageGroup`, `gender`, `clickedAt` (camelCase).
- 같은 문서의 재계산 SQL 과 `click_events_kafka` 정의: `notification_id`, `campaign_id`, `age_group`, `gender`, `clicked_at` (snake_case).
- ClickHouse `JSONEachRow` 는 **키 이름이 열 이름과 같아야** 읽는다. 둘 중 하나로 통일해야 한다.
- **확정: snake_case 로 통일** (ClickHouse 열 이름과 같아서 변환이 없다). Kafka 메시지와 S3 파일 모두 같은 형식을 쓴다.
- `clicked_at` 의 문자열 형식(`YYYY-MM-DD hh:mm:ss`, UTC 여부)도 함께 정한다. **확정: 한국 시간 그대로, 초 단위.**

### 6-4. 재계산과 파일의 관계

- 같은 클릭이 Kafka 재전송으로 두 번 저장될 수 있다 → 재계산 SQL 이 `notification_id` 로 한 번만 센다 (5절).
- 날짜가 두 개면 경로를 두 번 쓰거나 와일드카드로 넓힌다.
- 대상 기간: **최근 48시간** (확정, 5절 4-4). 늦게 온 클릭도 이 안에서 확정된다.

---

## 7. Kafka 메시지 (토픽 `click-events`)

| 항목 | 값 |
|---|---|
| 토픽 | `click-events` |
| 파티션 | 3 (시작값, 늘릴 수 있음) |
| 키 | `campaign_id` (같은 캠페인은 같은 파티션) |
| 값 | 6-2 와 같은 JSON 한 개 |
| 전달 보장 | 최소 한 번 (중복 가능) → 클릭 측은 `notification_id` 로 제거 |
| 보관 기간 | 결정 필요 (추천 7일, S3 장애를 견디는 기간) |

- 발행 주체: `OutboxRelay` (outbox 행 → Kafka). 페이로드는 `outbox_events.payload` 와 같은 형식.
- 소비 주체: ClickHouse Kafka 엔진 (`click_events_kafka`), 재집계 서비스 (S3 저장).

### 7-1. outbox `payload` (JSONB)

```json
{"notification_id":"...","campaign_id":12,"age_group":"20s","gender":"female","clicked_at":"2026-11-01 07:30:12"}
```

---

## 8. Redis 키 (캐시, DB 아님)

원문 6-3. **2주차에 넣는다 (2026-10-29 확정).** 2주차 상위 5 API 와 같이 Redis 1분 캐시를 붙인다.

| 키 | 타입 | 내용 | TTL |
|---|---|---|---|
| `ad:clicks:{yyyyMMddHHmm}` | Sorted Set | campaign_id → 그 분의 클릭 수 | 2시간 |
| `ad:top:{window}:{yyyyMMddHHmm}` | Sorted Set | 윈도우 상위 결과 캐시 (상위 5개) | 1분 |
| `campaign:candidates:{ageGroup}` | String(JSON) | 연령대별 후보 캠페인 목록 | 30~60초 |

- 상위 N 캐시와 후보 캐시는 Redis 로 한다 (그림대로). Redis 는 2주차 상위 5 API 와 같이 넣으며, 아래 키 설계를 그대로 쓴다 (확정).
- 캐시 키에는 윈도우 크기를 넣는다 (1분 결과를 5분 질의에 재사용하지 않음).
- 캠페인 생성·수정·상태 변경 때 후보 캐시를 지운다. 누락 시 최대 TTL 만큼 옛 목록이 나온다 (허용 최대 지연 60초, 고도화 8절 5번).

---

## 9. ID 체계

원문 6-4.

| 항목 | 현재 | 제안 |
|---|---|---|
| `notification_id` | UUID 문자열 (36자) | 유지. 노출 1건의 전역 식별자, 중복 제거 키 |
| `campaign_id` | BIGINT | 유지. 모든 집계의 기준 |
| `click_id` | BIGINT 자동 증가 | 유지. 원본 순번 |
| `outbox_id` | 없음 | [Kafka] BIGSERIAL |
| `minute_bucket` | 없음 | [지금] TIMESTAMP (분 단위), 분 버킷 표 키의 일부 |
| `recompute_id` | 없음 | [제안] IDENTITY (2-8) |

---

## 10. 윈도우 규칙 (질의 기준)

원문 3-6, 4-5. 저장 규칙과 질의 규칙이 이 명세를 따른다.

- 저장: **1분 칸** 하나에 한 번만 쓴다 (분 버킷).
- 질의: "최근 M분" = 그 기간의 칸을 **합한다** (`minute_bucket >= now − M분`).
- 입력 `windowMinutes`: 기본 1, 최대 60, **1의 배수만**.
- 윈도우 크기마다 표를 따로 두지 않는다.
- 윈도우를 저장하지 않는 이유: 클릭 1건이 M개 윈도우에 들어가서 행이 M배가 되고, 겹치는 값을 다시 더하면 중복이 생긴다.

---

## 11. 원문 불일치 · 결정 필요 (정본 기준 처리)

| # | 내용 | 원문 위치 | 처리 |
|---|---|---|---|
| 1 | 구체화 뷰 개수 | 11-3 "3개" vs 6-2·D8 "2개" | **2개로 고침** (11-3 수정 완료) |
| 2 | S3·Kafka 키 이름 camelCase vs snake_case | 3-8 vs 3-8 재계산 SQL, 6-2 | **확정: snake_case** (6-3) |
| 3 | `top_ads_snapshot` 만들지 말지 | 6-1 ⑧·11-2 vs 3-3·3-6 | **확정: 만들지 않음** (2-9) |
| 4 | 노출·클릭의 연령대 미상 값 (NULL vs `unknown`) | 6-1 ① (NULL) vs ② (`unknown`) | **확정: `unknown`** (3절 백필 때 적용) |
| 5 | `campaign_age_groups`, `campaign_genders` 의 PK 여부 | 6-1 ④ "확인 후 생성" | 생성 전에 DB 에서 확인 |
| 6 | `recompute_log` DDL 없음 | 11-2 "추가", 3-8 목록만 | **확정: 2-8 제안안 승인** |
| 7 | 운영 Flyway 적용 방식 | 3-6 주의 | **확정: 수동 SQL 적용** (4절) |
| 8 | `click_raw` 조회 시 `FINAL` 사용 여부 | 6-2 설명만 | 조정 쿼리 작성 때 결정 |
| 9 | Redis 도입 시점 | 결정 15 | 조회 API 와 같이 vs 이월 |
| 10 | Kafka 보관 기간 | 결정 4 | 추천 7일 |
| 11 | `docs/ARCHITECTURE.md` (레포) 가 옛 구조 | develop 기준 `target_age_group`, `click_facts` 에 연령대·성별 없음, `click_raw` 없음 | **레포 PR 로 고친다** (O8, 4주차 이후) |
| 12 | `시스템_아키텍처.png` 에 조정·지표 수집 없음 | 고도화 12절 6 | 그림 수정 PR 과 함께 |
| 13 | 3-2 옛 PK `(campaign_id, minute_bucket)` | 3-2-2 | 6-1 ② 의 PK 4열이 **정본**. 3-2 는 옛 내용 |

---

## 12. 변경 목록 요약 (정본에서 자동으로 뽑는 체크리스트)

**PostgreSQL**
- [ ] [지금] `ad_impressions` 열 추가 (2-1)
- [ ] [지금] `click_events` 열 추가 (2-1)
- [ ] [지금] `campaigns.bid_price` 추가 (2-2)
- [ ] [지금] `campaign_minute_stats` + `idx_cms_minute` (2-3)
- [ ] [지금] `campaign_budget`, `campaign_slot_budget`, `refund_ledger` (2-6)
- [ ] [지금] 인덱스 정정 4개 (2-5)
- [ ] [지금] 백필 (3절)
- [ ] [Kafka] `outbox_events` (2-7)
- [x] [확정] `recompute_log` (2-8)
- [ ] [유지] `campaign_stats` (2-4)
- [x] [제외] `top_ads_snapshot` (확정: 만들지 않음, 2-9)

**ClickHouse** (4주차)
- [ ] `click_events_kafka`, `click_raw`, `click_facts`, `click_facts_stage`, 구체화 뷰 2개 (5절)
- [ ] [4주차, 계획 포함] `click_facts_hour`, `click_facts_day` (및 구체화 뷰 2개, 5절)
- [ ] [제외] 노출용 표

**S3**
- [ ] 경로 규칙, JSON Lines 형식, 파일 닫는 기준 (6절)

**Kafka**
- [ ] 토픽 `click-events`, 파티션 3, 키 `campaign_id` (7절)

**Redis**
- [ ] 키 3종 (8절)

**문서**
- [ ] `docs/ARCHITECTURE.md` PR (11번 항목)
- [ ] `시스템_아키텍처.png` 수정 (12번 항목)
- [ ] `.env.example` 에 접속 변수 이름 추가 (Kafka, ClickHouse, S3, Redis)
