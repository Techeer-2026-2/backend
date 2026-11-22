---
id: 2026-11-22-ad-pipeline-kafka-clickhouse
title: 광고 클릭·노출 집계를 Kafka와 ClickHouse로 옮기고 확정은 매일 재계산한다
zone: design
status: 제안
recorder: kmssss56
ai: Claude
recorded: 2026-11-22
discussed: 2026-10-29~2026-11-22
refs: -
files: docs/고도화/고도화_개발지시서.md
summary: 클릭은 DB 없이 Kafka로 보내고 집계는 ClickHouse MV, 원본은 S3, 확정값은 매일 03:00 재계산으로 맞추며 Redis는 추가하지 않는다
---

## 왜
- 클릭마다 `campaign_stats` 한 행을 갱신해서 같은 캠페인에 클릭이 몰리면 행 잠금이 생긴다.
- 추이·연령대 조회가 원본 표를 매번 세서 데이터가 쌓일수록 느려진다.
- 재계산을 하려면 원본이 있어야 하는데 지금은 DB 원본만 있다.

## 결정
- 클릭: API → Kafka `ad-clicked` 직접 발행. 요청 경로에서 DB 쓰기 없음. 응답 201 (전송됨), 202 (실패분을 `click_fallback`에 보관), 503 (둘 다 실패). 실패분은 재전송기가 Kafka로 다시 보낸다. DB 사본 `click_events`는 사본 컨슈머가 배치로 쓴다.
- 노출: 한 트랜잭션으로 차감·기록·outbox 저장 → 릴레이가 Kafka `ad-impressed` 발행 (`db/2026-11-22-banner-request-id-and-outbox` 참고).
- 집계: ClickHouse Kafka 엔진 + MV로 분·시간·일 표를 채운다 (실시간, 잠정값).
- 원본: 재계산 서비스가 Kafka를 읽어 S3 `raw-clicks/`, `raw-impressions/`에 저장한다 (저장 성공 후 커밋).
- 확정: 매일 03:00 최근 2일을 S3에서 다시 세서 ClickHouse 파티션을 교체한다. 중복은 `notification_id`로 한 번만 센다.
- 조정: 매일 03:30 개수 4쌍을 비교하고 차이는 경고만 남긴다.
- Redis: 추가하지 않는다.

## 대안
| 대안 | 기각 이유 |
|---|---|
| 클릭을 Redis에서 중복 확인 후 Kafka | 요청마다 Redis 호출이 생기고 Redis 장애 대응이 복잡함. 중복은 재계산이 이미 정리 |
| 클릭을 ClickHouse에 직접 쓰기 | ClickHouse 장애 때 클릭이 실패하고, 원본 S3 저장 코드가 따로 필요함 |
| ClickHouse에서 실시간 중복 제거 (ReplacingMergeTree) | 병합 시점을 보장할 수 없어서 실시간 값이 항상 맞지 않음 |
| Kafka Connect S3 Sink | 프로그램 하나가 더 늘어남. 재계산 서비스가 직접 저장 |

## 되돌릴 조건
- 재계산 중 MV와 교체가 같은 날짜에서 충돌하는 것이 실험으로 확인되면 재계산 시각과 방식을 다시 본다.
- 운영 브로커를 1대로 두면 디스크 장애 때 7일치 메시지가 사라질 수 있다. 운영 전에 3대로 올릴지 다시 본다.
- 클릭 수 조정 차이가 반복되면 저장 단계(S3)부터 본다.
