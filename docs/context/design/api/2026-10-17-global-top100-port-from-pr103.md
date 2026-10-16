---
id: 2026-10-17-global-top100-port-from-pr103
title: 전체 Top 100·순위 변동은 PR #103 의 마지막 커밋만 최신 develop 위에 옮겨 구현한다
zone: design
status: 제안
recorder: kmssss56
ai: Claude
recorded: 2026-10-17
discussed: 2026-10-17
refs: "#102, #103"
files: domain/ranking/service/RankingCalculator.java, domain/ranking/repository/RankingRepository.java, db/ranking/V3__global_top_100.sql
summary: 옛 랭킹 구현이 딸려 와 충돌하는 PR #103 을 그대로 합치지 않고, 새 변경(전체 Top 100·rankChange)만 develop 의 Top 50 기준에 맞춰 다시 적용했다
---

## 왜
#103 은 옛 랭킹 구현(10/3 커밋)을 다시 포함한 브랜치라 develop 과 add/add 충돌이 20개 파일에서 난다. 새 변경은 마지막 커밋 하나(16개 파일)뿐이고, 그것도 주변 차트가 Top 20 이던 시절 코드 위에서 작성됐다. develop 은 이미 Top 50(V2)이다.

## 결정
- 전체 차트는 곡별 고유 완주 청취자 20명 이상인 Top 100, 주변 차트는 develop 의 Top 50·곡별 3명 이상을 유지한다
- `rankChange` = 직전 한 시간 순위 − 현재 순위(양수 상승). 직전 시간에 없던 곡은 null 이라 응답에서 빠진다
- 순위 상한 100 마이그레이션은 `V1_1` 이 아니라 **`V3`** 이다. 이미 V2 가 적용된 DB 에 번호가 더 낮은 `V1_1` 을 넣으면 Flyway 검증이 실패해 서버가 뜨지 않는다
- 원본 `RankingRepositoryQueryTest` 는 H2 였다. 실제 PostgreSQL(Testcontainers)로 바꾸고 `build.gradle` 의 h2 추가는 옮기지 않았다
- 데모 데이터는 직전 두 시간을 곡 배정을 한 칸 밀어 채워 화면에서 순위 상승·하락을 볼 수 있게 했다

## 대안
| 대안 | 기각 이유 |
|---|---|
| #103 을 develop 에 직접 머지 | 충돌 20개 파일, 해결을 잘못하면 develop 의 랭킹 수정(Top 50·회원 식별)이 되돌려진다 |
| #103 작성자가 rebase 하도록 요청 | 작성자가 같은 문제를 반복하고 있어 대신 옮겼다 (미확인: 작성자와 이 방식에 합의한 것은 아님) |

## 한계
- 배포 직후 저장돼 있던 전체 차트는 이전 규칙의 결과라 다음 정각 배치까지 유지된다(수동 재집계는 `docs/RANKING.md`)
- 전체 차트 기준이 곡별 3명에서 20명으로 높아져 데이터가 적은 동안 전체 차트가 비어 보일 수 있다

## 되돌릴 조건
전체 차트가 계속 비어 보이면 곡별 기준(`MIN_GLOBAL_TRACK_LISTENERS`)을 다시 정한다.
