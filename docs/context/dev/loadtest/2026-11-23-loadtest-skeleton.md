---
id: 2026-11-23-loadtest-skeleton
title: 부하 테스트는 별도 DB·loadtest 프로필·고정 시드로 돌린다
zone: dev
status: 제안
recorder: github-id
ai: Claude
recorded: 2026-11-23
discussed: 2026-11-23
refs: -
files: loadtest/, src/main/resources/application-loadtest.yml, build.gradle
summary: 부하 테스트는 같은 PostgreSQL 안의 별도 DB 에 고정 시드를 넣고 local,loadtest 프로필로 측정한다
---

## 왜
고도화 단계마다 같은 시나리오·같은 데이터 양으로 다시 재야 하는데, 측정 전 초기화(TRUNCATE)를 개발 DB 에 하면 개발 데이터가 사라진다.
local 프로필은 show-sql·DEBUG 로그가 켜져 있어 응답 시간이 로그 출력에 좌우되고, actuator 는 health,info 만 열려 있어 앱 지표를 볼 수 없었다.
#159 로 배너 매칭이 회원 연령대·성별 기준이 되어 시나리오 A 를 targetUserId 대신 X-Member-Id 로 호출해야 한다.

## 결정
- DB: 같은 컨테이너 안의 `commute_playlist_loadtest`. reset.sql 은 DB 이름에 loadtest 가 없으면 실패한다
- 프로필: `local,loadtest`. SQL 로그를 끄고 metrics·prometheus 엔드포인트와 http 요청 히스토그램을 연다. prod 에는 쓰지 않는다
- 시드: 회원 1,000(연령대 5종, 10% 성별 없음), 광고주 1, ACTIVE 캠페인 100(1번은 전 타겟·높은 입찰가인 핫 캠페인), campaign_stats 미리 생성. setseed 로 매번 같은 데이터
- k6 는 constant-arrival-rate, 워밍업 30초 후 측정 3분. 측정 구간만 phase:measure 태그로 따로 집계
- SSE(C) 는 Node fetch 클라이언트로 구독자를 유지하고 이벤트 간격 - 2초를 지연으로 기록
- 기준값: A 600/1500 rps, B 300/800 묶음/s(정상/과부하), C 구독자 100, D A300+B150+SSE100, 측정 2분. 로컬 단계 측정에서 A 는 800~1200, B 는 500~600 사이에서 p95 가 ~10ms → ~1s 로 꺾였다. 처음 정한 A 800·B 400 은 3회 중 1~2회가 무너져 낮췄다
- 꺾일 때 CPU 20% 미만, 커넥션 대기 ~190 이라 풀(10)이 비교를 좌우한다. 기준선은 풀 10 으로 확정하고, 풀 30 은 Kafka(P5) 뒤 별도 단계로 바꾼다
- 풀 30 대조: B 800 은 3회 모두 p95 3.6ms, 한계는 1000~1500 묶음/s 이고 그때 잠금 대기의 대부분이 예산 차감(UPDATE campaigns)이었다(campaign_stats UPSERT 의 약 32배)

## 대안
| 대안 | 기각 이유 |
|---|---|
| 개발 DB 를 그대로 초기화 | 개발 데이터가 매번 지워진다 |
| normal 을 한계의 80% (A 800, B 400) | 한계 근처라 3회 중 1~2회가 무너져 중앙값 비교가 운에 좌우됐다 |
| k6 로 SSE 측정 | 문서(고도화 계획 3-1)에서 장시간 SSE 유지가 어렵다고 정했다 |

## 되돌릴 조건
2차 측정(배포 사양)에서 DB 를 별도 인스턴스로 띄우게 되면 DB 설정 부분을 다시 정한다.
Kafka 적용 후에도 예산 차감이 DB 핫 로우로 남아 한계가 오르지 않으면 예산 차감을 Redis 카운터로 옮길지 다시 본다.
