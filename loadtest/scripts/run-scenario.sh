#!/usr/bin/env bash
# 시나리오 1회 측정. backend 폴더에서 실행한다. 같은 값으로 3번 돌리고 중앙값을 기록한다.
#   bash loadtest/scripts/run-scenario.sh A 1                                  # 정상 부하 (A 600 rps)
#   RATE=1500 LABEL=overload bash loadtest/scripts/run-scenario.sh A 1         # 과부하
#   bash loadtest/scripts/run-scenario.sh B 2                                  # 정상 부하 (B 300 묶음/s)
#   RATE=800 LABEL=overload bash loadtest/scripts/run-scenario.sh B 2          # 과부하
#   SUBSCRIBERS=100 bash loadtest/scripts/run-scenario.sh C 1
#   bash loadtest/scripts/run-scenario.sh D 3                                  # A 300 + B 150 + SSE 100
# 기준값 근거: loadtest/README.md "기준값". STAGE 로 단계 이름을 붙인다(기본 00_기준선).
# 결과는 loadtest/results/{STAGE}/{시나리오}[_{LABEL}]_run{N}/ 에 쌓인다.
set -euo pipefail
source "$(dirname "$0")/env.sh"

SCENARIO="${1:?시나리오 A|B|C|D}"
RUN="${2:-1}"
STAGE="${STAGE:-00_기준선}"
WARMUP_SEC="${WARMUP_SEC:-30}"
MEASURE_SEC="${MEASURE_SEC:-120}"

# LABEL 로 같은 시나리오의 부하 지점을 구분한다(예: normal, overload)
OUT_DIR="$LOADTEST_DIR/results/$STAGE/${SCENARIO}${LABEL:+_$LABEL}_run${RUN}"
mkdir -p "$OUT_DIR"
echo "결과 폴더: $OUT_DIR"

curl -s "$BASE_URL/actuator/metrics/hikaricp.connections.max" > "$OUT_DIR/pool.json" || true
echo "[1/5] 데이터 초기화 + 시드"
psql_lt -q < "$LOADTEST_DIR/seed/reset.sql"
psql_lt -q < "$LOADTEST_DIR/seed/seed.sql" > "$OUT_DIR/seed.txt"

echo "[2/5] pg_stat_statements 초기화"
psql_lt -qtAc "SELECT pg_stat_statements_reset();" > /dev/null

echo "[3/5] 잠금 샘플러 시작"
bash "$LOADTEST_DIR/scripts/lock-sampler.sh" "$OUT_DIR/locks.csv" &
SAMPLER_PID=$!
SSE_PID=""
cleanup() {
  kill "$SAMPLER_PID" 2>/dev/null || true
  [ -n "$SSE_PID" ] && kill "$SSE_PID" 2>/dev/null || true
}
trap cleanup EXIT
trap 'echo "실패: $OUT_DIR 의 로그(k6.txt, sse.log)를 확인하세요" >&2' ERR

start_sse() {
  OUT="$(winpath "$OUT_DIR/sse.json")" DURATION_SEC=$((WARMUP_SEC + MEASURE_SEC)) WARMUP_SEC="$WARMUP_SEC" BASE_URL="$BASE_URL" \
    node "$(winpath "$LOADTEST_DIR/sse/sse-subscribers.mjs")" > "$OUT_DIR/sse.log" 2>&1 &
  SSE_PID=$!
}

run_k6() {
  k6 run \
    -e BASE_URL="$BASE_URL" -e WARMUP="${WARMUP_SEC}s" -e MEASURE="${MEASURE_SEC}s" \
    --summary-export "$(winpath "$OUT_DIR/k6-summary.json")" \
    ${K6_PROMETHEUS:+--out experimental-prometheus-rw} \
    "$@" 2> >(tee "$OUT_DIR/k6-progress.log" >&2) | tee "$OUT_DIR/k6.txt"
}

echo "[4/5] 시나리오 $SCENARIO 실행 (워밍업 ${WARMUP_SEC}s + 측정 ${MEASURE_SEC}s)"
case "$SCENARIO" in
  A) run_k6 -e RATE="${RATE:-600}" "$(winpath "$LOADTEST_DIR/k6/a_impression.js")" ;;
  B) run_k6 -e RATE="${RATE:-300}" "$(winpath "$LOADTEST_DIR/k6/b_click.js")" ;;
  C) start_sse; wait "$SSE_PID"; SSE_PID="" ;;
  D) start_sse
     run_k6 -e RATE_A="${RATE_A:-300}" -e RATE_B="${RATE_B:-150}" "$(winpath "$LOADTEST_DIR/k6/d_mixed.js")"
     wait "$SSE_PID" || true; SSE_PID="" ;;
  *) echo "알 수 없는 시나리오: $SCENARIO"; exit 1 ;;
esac

echo "[5/5] DB 지표 수집"
cleanup
psql_lt < "$LOADTEST_DIR/scripts/top-sql.sql" > "$OUT_DIR/top-sql.txt"
psql_lt < "$LOADTEST_DIR/scripts/explain.sql" > "$OUT_DIR/explain.txt"
psql_lt -c "SELECT campaign_id, sent_count, click_count FROM campaign_stats ORDER BY click_count DESC LIMIT 5;" \
  > "$OUT_DIR/campaign-stats.txt"

# 앱 지표: 측정 구간의 커넥션 대기·사용 중 커넥션·CPU 최댓값 (Prometheus 가 없으면 건너뛴다)
PROM_URL="${PROM_URL:-http://localhost:9090}"
{
  for q in hikaricp_connections_pending hikaricp_connections_active process_cpu_usage; do
    v=$(curl -s "$PROM_URL/api/v1/query" --data-urlencode "query=max_over_time(${q}[${MEASURE_SEC}s])" \
      | grep -o '"value":\[[^]]*\]' | sed 's/.*,"\(.*\)"\]/\1/' | head -1)
    echo "${q}_max=${v:--}"
  done
} > "$OUT_DIR/app-metrics.txt" 2>/dev/null || true
cat "$OUT_DIR/app-metrics.txt" 2>/dev/null || true

# 잠금 대기: 측정 구간(워밍업 이후) 최댓값·평균
awk -F, -v skip="$WARMUP_SEC" 'NR==1{next} !t0{t0=$1} ($1-t0)>=skip {n++; s+=$2; if($2>m)m=$2}
  END{ if(n) printf "lock_waits max=%d avg=%.2f samples=%d\n", m, s/n, n; else print "lock_waits 샘플 없음" }' \
  "$OUT_DIR/locks.csv" | tee "$OUT_DIR/locks-summary.txt"

echo
echo "완료. 기록할 값: k6.txt(p50/p95/p99, 에러율), locks-summary.txt, top-sql.txt, sse.json"
echo "Grafana 캡처 이름: ${STAGE}_${SCENARIO}.png"
