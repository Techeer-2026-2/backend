---
id: 2026-11-22-banner-request-id-and-outbox
title: 배너 요청 ID로 이중 차감을 막고 노출 발행은 DB 아웃박스로 보낸다
zone: design
status: 제안
recorder: kmssss56
ai: Claude
recorded: 2026-11-22
discussed: 2026-11-21~2026-11-22
refs: -
files: docs/고도화/ERD_데이터명세.md
summary: 배너 요청마다 X-Request-Id를 받아 banner_requests 유니크 표로 한 번만 차감하고, 노출 발행은 같은 트랜잭션의 outbox 행을 릴레이가 Kafka로 보낸다
---

## 왜
- 네트워크 재시도로 배너 요청이 두 번 오면 예산이 두 번 빠진다.
- 차감은 Kafka 발행과 한 트랜잭션으로 묶을 수 없어서, 차감은 됐는데 발행이 빠지는 일이 생길 수 있다.

## 결정
- 프론트(음악 앱 `getMatchedBanner`)가 요청마다 `X-Request-Id`(UUID)를 보낸다. 헤더는 필수다 (없으면 400).
- 서버는 한 트랜잭션에서 `banner_requests`에 요청 ID를 먼저 넣는다. 이미 있으면 차감과 노출 없이 저장된 결과를 돌려준다.
- 같은 트랜잭션에서 차감(조건부 UPDATE), `ad_impressions`, `campaign_stats.sent_count`, `outbox` 행을 함께 저장한다.
- 릴레이(1초마다)가 `outbox`의 PENDING 행을 Kafka `ad-impressed`로 보내고 PUBLISHED로 바꾼다.
- 발행 후 상태 변경 전에 죽으면 같은 노출이 두 번 나갈 수 있다. 재계산이 `notification_id`로 정리한다.

## 대안
| 대안 | 기각 이유 |
|---|---|
| 요청 ID 없이 서버가 중복 판단 | 같은 사용자의 정상 재요청과 재시도를 구분할 수 없음 |
| 차감 후 바로 Kafka 발행 | 발행 실패 시 노출이 집계에서 빠지고 되돌릴 방법이 없음 |
| CDC (Debezium) | 커넥터를 운영해야 해서 1인 모놀리식 구성에 과함 |

## 되돌릴 조건
- 노출 1번당 DB 쓰기가 2회 늘어난 부하가 측정에서 문제가 되면 `ad_impressions`를 대기열로 쓰는 방식(번호만 기억)을 다시 본다.
- `banner_requests`는 7일 뒤 삭제하는데, 7일 넘은 재시도가 실제로 있는지 보고 기간을 다시 정한다.
