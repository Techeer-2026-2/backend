# ⚠️ 배포 전 필수 체크 — 광고 파이프라인 (Kafka · ClickHouse · 아웃박스)

> 이 문서를 읽지 않고 배포하면 앱이 뜨지 않거나 배너가 나가지 않습니다. 배포 담당자는 **위에서부터 순서대로** 확인하세요.

## 🚫 막는 항목 (이것 없이 배포하면 실패)

| # | 확인 | 이유 |
|---|---|---|
| 1 | **ClickHouse 가 먼저 떠 있어야 함** (`CLICKHOUSE_URL` 설정) | `clickhouse.init-schema: true` 기본값. ClickHouse 에 연결 못 하면 **앱이 기동하지 않음** |
| 2 | **Kafka 브로커와 `KAFKA_BOOTSTRAP_SERVERS` 설정** | 기본값이 `localhost:9092` 라서 운영에서는 연결 실패 |
| 3 | **토픽 생성**: `ad-impressed`, `ad-clicked` (파티션 3, 보관 7일 = 604800000) | `docker-compose.yml` 의 `kafka-init` 은 로컬용. 운영 compose 에는 없음 |
| 4 | **운영 DB SQL 먼저 적용** | 새 코드가 `outbox`, `banner_requests`, `click_fallback` 표를 바로 씀. 순서는 `운영_DB_적용_순서.md` |
| 5 | **프론트(#84) 먼저 배포** | `/banners/match` 는 `X-Request-Id` 헤더가 **필수**. 프론트가 안 보내면 400 |

> ⚠️ 현재 `docker-compose.prod.yml` 에는 Kafka·ClickHouse 서비스와 `KAFKA_BOOTSTRAP_SERVERS`·`CLICKHOUSE_URL` 설정이 **없습니다**. 위 1~3을 해결하지 않으면 배포는 실패합니다. 이 부분은 아직 고치지 않았습니다.

## 📋 배포 순서

1. 운영 DB 확인 (`\d campaigns`, `\d ad_impressions`, `\d click_events`) 및 백업
2. 운영 DB SQL 적용: `001 → 002 → 003 → 005 → 006` (004 는 이미 있는지 확인). 자세한 명령은 `운영_DB_적용_순서.md`
3. ClickHouse 기동 + 스키마 확인 (`src/main/resources/clickhouse/001_schema.sql`)
4. Kafka 기동 + 토픽 2개 생성
5. 백엔드 환경변수: `KAFKA_BOOTSTRAP_SERVERS`, `CLICKHOUSE_URL`, `CLICKHOUSE_USER`, `CLICKHOUSE_PASSWORD`, `JWT_SECRET`
6. 프론트 #84 배포
7. 백엔드 배포 후 확인: 배너 요청(`X-Request-Id` 포함) → 200, 같은 ID 재요청 → 같은 결과, 클릭 → 201

## 🔁 되돌리기 (문제가 생기면)

- 백엔드: 직전 이미지로 롤백
- 운영 DB: 표는 `DROP TABLE` 로 제거 가능. **005(데이터 변경)는 백업에서 복원**
- 프론트: 직전 버전으로 롤백

## 📌 알아둘 것

- DB 사본(`click_events` 에 쓰는 컨슈머)은 제거했습니다. `click_events` 는 요청 경로에서 더 이상 쓰이지 않습니다.
- 클릭 집계의 실시간 값은 ClickHouse 가 만들고, 확정 값은 매일 03:00 재계산이 만듭니다.
