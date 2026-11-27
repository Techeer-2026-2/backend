# 부하 테스트 (loadtest)

고도화 단계마다 **같은 시나리오·같은 데이터 양**으로 측정해 개선 여부를 판단한다.
판단 기준: p95 가 줄고 에러율이 늘지 않으면 개선. 3회 중앙값으로 비교한다. 계획은 `docs/고도화/고도화_계획.md` 3-1절.

## 준비물

| 도구 | 설치 | 용도 |
|---|---|---|
| Docker Desktop | - | DB, 백엔드, Prometheus, Grafana |
| k6 | `winget install k6` / `brew install k6` | 시나리오 A·B·D |
| Node 18+ | - | 시나리오 C (SSE 구독자) |
| Git Bash | - | Windows 에서 `scripts/*.sh` 실행 |

## 구성

```
loadtest/
  docker-compose.loadtest.yml  # 기본 compose 덮어쓰기: pg_stat_statements, loadtest 프로필, 모니터링
  prometheus/prometheus.yml    # backend /actuator/prometheus, postgres-exporter 수집
  grafana/provisioning/        # Prometheus 데이터소스 (대시보드 JSON 은 dashboards/ 에 넣는다)
  seed/reset.sql, seed.sql     # 측정마다 같은 데이터로 되돌린다
  k6/a_impression.js           # A. 노출
  k6/b_click.js                # B. 노출 → 클릭 1:1
  k6/d_mixed.js                # D. A + B (C 는 스크립트가 함께 띄운다)
  sse/sse-subscribers.mjs      # C. SSE 구독자 N명
  scripts/setup.sh             # 최초 1회 환경 준비
  scripts/run-scenario.sh      # 시나리오 1회 측정 + DB 지표 수집
  scripts/lock-sampler.sh      # 잠금 대기 1초 샘플링
  scripts/top-sql.sql, explain.sql
  results/                     # 측정 결과 (git 에 올리지 않는다)
```

- DB 는 개발 DB 와 분리된 `commute_playlist_loadtest` 를 쓴다(같은 컨테이너). `reset.sql` 은 이름에 loadtest 가 없는 DB 에서는 실패한다.
- 백엔드는 `local,loadtest` 프로필로 뜬다. SQL 로그를 끄고 `/actuator/prometheus` 를 연다.
- 시드: 회원 1,000명(user_id 1~1000), 광고주 1명, 진행 중 캠페인 100개. **1번 캠페인이 핫 캠페인**(모든 타겟 + 높은 입찰가).
- 광고주 로그인: `lt-advertiser@loadtest.local` / `loadtest-pass-1234` (로컬 부하 테스트 DB 전용 값).

## 기준값

2026-11-23 로컬에서 부하를 단계적으로 올려 정했다(`results/98_knee_A`, `98_knee_B`). **단계마다 바꾸지 않는다.**

| 시나리오 | normal (한계의 60~70%) | overload (한계의 ~1.5배) | 측정한 한계 |
|---|---|---|---|
| A 노출 | 600 rps | 1500 rps | 800~1200 rps 사이에서 p95 24ms → 1.4s |
| B 노출→클릭 | 300 묶음/s | 800 묶음/s | 500~600 사이에서 p95 9ms → 1.1s |
| C SSE | 구독자 100명 | - | - |
| D 혼합 | A 300 + B 150 + SSE 100 | - | DB 트랜잭션 ~600/s (한계 ~1000/s 의 60%) |

- 한계를 넘으면 CPU 는 20% 미만인데 커넥션 대기가 ~190 이 된다(풀 10 이 핫 로우 잠금으로 오래 잡힘).
- 그래서 **커넥션 풀은 `DB_POOL_SIZE=10` 으로 고정**한다. 바꾸면 단계 간 비교가 섞인다. 대조 측정할 때만 바꾸고 기록에 적는다.
- 단계마다 볼 것: normal 의 p95(나빠지지 않았나), overload 의 p95(무너지지 않나), 그리고 단계적으로 올려 찾은
  "p95 100ms 이하·에러 1% 미만을 지키는 최대 RPS".
- Kafka 단계는 API 응답만 보면 안 된다: consumer lag 가 측정 중 계속 늘지 않는지, 클릭 → 집계 반영 지연 p95,
  측정 후 클릭 원본 수 = 집계 수(유실·중복 0)를 함께 기록한다.
- ClickHouse 단계는 이 시드로는 효과가 안 보인다. 노출 천만 건 규모의 분석용 시드를 따로 만들고 분석 API·대시보드를 비교한다.

## 실행 (backend 폴더에서)

```bash
# 한 단계 전체(6개 경우 × 3회, 약 55분) → results/{STAGE}/summary.md 에 중앙값 표
bash loadtest/scripts/run-baseline.sh
STAGE=01_분버킷 bash loadtest/scripts/run-baseline.sh
ONLY="B_normal B_overload" RUNS=1 bash loadtest/scripts/run-baseline.sh   # 일부만

# 표만 다시 만들기
node loadtest/scripts/summarize.mjs loadtest/results/00_기준선

# 커넥션 풀 대조 측정 (백엔드를 풀 30 으로 다시 띄운 뒤 → 끝나면 10 으로 되돌린다)
DB_POOL_SIZE=30 docker compose -f docker-compose.yml -f loadtest/docker-compose.loadtest.yml up -d backend
STAGE=90_대조_pool30 ONLY="A_overload B_normal B_overload" RUNS=1 bash loadtest/scripts/run-baseline.sh
docker compose -f docker-compose.yml -f loadtest/docker-compose.loadtest.yml up -d backend
```

개별 실행:

```bash
# 1) 최초 1회: DB·백엔드·모니터링 기동, 부하 테스트 DB 생성, 시드
bash loadtest/scripts/setup.sh

# 2) 시나리오별 3회. 매 회 reset → seed → pg_stat_statements_reset → 측정 → 수집
for n in 1 2 3; do bash loadtest/scripts/run-scenario.sh A $n; done
for n in 1 2 3; do bash loadtest/scripts/run-scenario.sh B $n; done
for n in 1 2 3; do SUBSCRIBERS=100 bash loadtest/scripts/run-scenario.sh C $n; done
for n in 1 2 3; do bash loadtest/scripts/run-scenario.sh D $n; done

# 다음 단계 측정은 STAGE 만 바꾼다
STAGE=01_분버킷 bash loadtest/scripts/run-scenario.sh B 1
```

| 변수 | 기본값 | 설명 |
|---|---|---|
| `RATE` | A=600, B=300 | 초당 요청(B 는 노출+클릭 묶음) |
| `RATE_A`, `RATE_B` | 300, 150 | D 의 노출·클릭 흐름 각각 |
| `LABEL` | (없음) | 결과 폴더 구분(normal, overload) |
| `DB_POOL_SIZE` | 10 | 백엔드 기동 시 커넥션 풀 크기 (compose 에 전달) |
| `SUBSCRIBERS` | 100 | C·D 의 SSE 구독자 수 |
| `CAMPAIGNS` | 1 | SSE 구독 캠페인(쉼표 구분, 돌아가며 배정) |
| `WARMUP_SEC`, `MEASURE_SEC` | 30, 120 | 워밍업·측정 시간 |
| `STAGE` | 00_기준선 | 결과 폴더·캡처 이름 |
| `K6_PROMETHEUS` | (없음) | 1 로 주면 k6 지표도 Prometheus 로 보낸다(Grafana 에서 앱 지표와 같이 보기) |

**부하 값은 위 "기준값" 표에서 바꾸지 않는다.**

## 결과 읽기

`results/{STAGE}/{시나리오}_run{N}/`

| 파일 | 기록할 값 |
|---|---|
| `k6.txt` | `http_req_duration{phase:measure,...}` p50/p95/p99, `http_req_failed` 에러율, `banner_filled` 배너 있음 비율, `click_new_duration` 새 클릭 p95, `dropped_iterations`(0 이 아니면 k6 쪽 VU 부족) |
| `locks-summary.txt` | 잠금 대기 최댓값·평균 (원본: `locks.csv`) |
| `top-sql.txt` | 호출 수 상위 10개 SQL |
| `explain.txt` | 후보 조회·통계 조회 실행 계획 |
| `sse.json` | 연결 성공/실패, 초당 이벤트 수(기대값과 비교), 이벤트 지연 p95 |
| `campaign-stats.txt` | 핫 캠페인 쏠림 확인 |

`click_duplicate` 는 0 이어야 한다(1:1 이 깨지면 중복 경로를 재게 된다).

Grafana(http://localhost:3000) 에서 같이 볼 것. 쿼리 예:

```promql
# 앱 p95 (URI 별)
histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket[30s])))
# 커넥션 대기 — 0 이어야 정상
hikaricp_connections_pending
process_cpu_usage
# DB 잠금 대기 (postgres-exporter)
sum(pg_locks_count{mode=~".*ExclusiveLock"})
```

기본 대시보드 **부하 테스트 개요**(http://localhost:3000/d/loadtest-overview)가 자동 등록된다: URI별 p95/p99·요청 수·5xx, HikariCP, CPU, 힙, DB 잠금(모드별)·세션·커밋.
앱 쪽 p95 는 서버 안에서 잰 값이라 Tomcat 대기열에서 기다린 시간이 빠진다. 기록하는 대표 수치는 k6 값이다.
더 필요하면 grafana.com 에서 4701(JVM Micrometer), 19665(k6 Prometheus), 9628(PostgreSQL) JSON 을 받아 `grafana/provisioning/dashboards/` 에 넣는다.
캡처 이름: `{단계번호}_{단계이름}_{시나리오}.png` (예: `00_기준선_A.png`).

## 정리

```bash
docker compose -f docker-compose.yml -f loadtest/docker-compose.loadtest.yml down
docker compose up -d   # 평소 개발 환경으로 (백엔드가 다시 local 프로필·개발 DB 로 뜬다)
```

## 이후 단계에서 추가할 것

- P5 Kafka: compose 에 kafka-exporter(consumer lag), redis_exporter 추가, `prometheus.yml` 에 수집 대상 추가
- P3 분 버킷: `explain.sql` 에 분 버킷 집계 쿼리 추가, 분 버킷 합계 = `click_events` 수 확인 쿼리 추가
- 2차 측정(배포 사양): `BASE_URL` 만 바꿔 같은 스크립트로 실행. 운영과 겹치지 않는 시간에, 운영 DB 가 아닌 곳에서

## 기준값을 정할 때 본 것 (2026-11-23)

- 처음 정한 normal(A 800, B 400, D A800+B400)은 3회 중 1~2회가 무너졌다(p95 수 ms ↔ 1.5s). D 는 DB 트랜잭션 ~1,600/s 로 이미 한계를 넘었다.
  그래서 normal 을 한계의 60~70% 로 낮췄다. 이전 결과는 `results/00_기준선_v1_폐기/`.
- 한계 근처에서는 한 번 커넥션 대기열이 생기면 빠지지 않고 측정 끝까지 무너진 상태로 남는다. overload 는 이 현상을 보는 용도라,
  p95 중앙값과 함께 **3회 중 몇 번 무너졌는지**를 기록한다.
- 과부하 에러는 앱 예외가 아니라 연결 단계 끊김(connection forcibly closed)이다.
- 측정 PC 의 여유 메모리가 1GB 까지 떨어진 적이 있다. 측정 전에 다른 프로그램을 닫는다.

## 커넥션 풀 대조 측정 (2026-11-23, `results/90_대조_pool30`, `91_한계_pool30`)

| B (묶음/s) | 풀 10 | 풀 30 |
|---|---|---|
| 800 | p95 1,544 / 4.0 / 1,253ms (3회 중 2회 무너짐) | 3.6 / 3.6 / 3.6ms, 커넥션 최대 6~9개 |
| 1000 | - | p95 4.7ms, p99 73ms, 에러 1.9%, 잠금 대기 max 21 (경계) |
| 1500 | - | p95 960ms, 에러 49%, 잠금 대기 avg 8.6 / max 25 (커넥션 30개 중 25개가 잠금 대기) |
| 2000 | - | p95 1,093ms, 에러 61% |

- 풀 10 의 800 붕괴는 처리 한계가 아니라 여유 부족이다(안 무너진 회차는 커넥션을 7개만 썼다).
- 풀 30 의 진짜 한계는 1000~1500 묶음/s. 이때 CPU 22% 인데 잠금 대기가 급증한다 → 핫 로우가 실제 병목.
- 잠금을 오래 잡는 쿼리는 **예산 차감 `UPDATE campaigns SET spent_total`(BannerService.matchBanner → deductBudget)** 이다.
  1500 에서 평균 20.8ms·총 1,829초로, `campaign_stats` UPSERT(평균 0.64ms·총 57초)의 약 32배.

## 다음 단계 계획 (2026-11-23 합의)

- 기준선은 **풀 10** 으로 확정한다(`results/00_기준선`).
- 한 단계에서 하나만 바꾼다: P5 Kafka(로컬, 풀 10) → P5b 풀 10→30(로컬) → 2차 배포 사양 측정(풀 30).
- P5 는 풀 10 과 풀 30 을 둘 다 재서 (기준선/Kafka) × (풀 10/30) 표를 채운다. 풀 30 기준선은 지금 B 만 있으니 그때 A·D 를 채운다.
- 배포에서 풀 30 을 쓰면 `application-prod.yml` 에 명시하고, Postgres `max_connections`(기본 100)와 인스턴스·consumer 수의 합을 확인한다.
