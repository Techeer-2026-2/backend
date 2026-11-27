#!/usr/bin/env bash
# 한 단계(STAGE)의 전체 측정을 돌린다: 시나리오·부하 지점마다 3회, 끝나면 중앙값 표(summary.md)를 만든다.
# backend 폴더에서 실행한다. 워밍업 30초 + 측정 2분, 1회 약 3분이라 전체 18회는 약 55분 걸린다.
#   bash loadtest/scripts/run-baseline.sh                       # STAGE=00_기준선
#   STAGE=01_분버킷 bash loadtest/scripts/run-baseline.sh
#   ONLY="B_normal B_overload" bash loadtest/scripts/run-baseline.sh   # 일부만
#
# 기준값(README "기준값" 참고). 단계마다 바꾸지 않는다.
#   A normal 600 rps / overload 1500 rps
#   B normal 300 묶음/s / overload 800 묶음/s
#   C SSE 구독자 100명
#   D A 300 + B 150 + SSE 100 (DB 트랜잭션 약 600/s, 측정한 한계 ~1000/s 의 60%)
set -uo pipefail
source "$(dirname "$0")/env.sh"
cd "$BACKEND_DIR"

export STAGE="${STAGE:-00_기준선}"
RUNS="${RUNS:-1 2 3}"
# 과부하에서는 응답이 1초 이상으로 느려져 VU 가 많이 필요하다.
export MAX_VUS="${MAX_VUS:-3000}" PRE_VUS="${PRE_VUS:-100}"

declare -A CASES=(
  [A_normal]="RATE=600"
  [A_overload]="RATE=1500"
  [B_normal]="RATE=300"
  [B_overload]="RATE=800"
  [C_normal]="SUBSCRIBERS=100"
  [D_normal]="RATE_A=300 RATE_B=150 SUBSCRIBERS=100"
)
ORDER="${ONLY:-A_normal A_overload B_normal B_overload C_normal D_normal}"

for run in $RUNS; do
  for c in $ORDER; do
    scenario="${c%%_*}"
    label="${c#*_}"
    echo "=== $(date +%H:%M:%S) $STAGE $c run$run (${CASES[$c]})"
    env ${CASES[$c]} LABEL="$label" bash "$LOADTEST_DIR/scripts/run-scenario.sh" "$scenario" "$run" \
      > "$LOADTEST_DIR/results/.last-run.log" 2>&1 \
      || echo "    실패 — $LOADTEST_DIR/results/.last-run.log 확인"
    grep -E "lock_waits|_max=" "$LOADTEST_DIR/results/.last-run.log" | tr '\n' ' '; echo
    # 과부하 뒤 밀린 요청이 다음 측정에 섞이지 않게 잠시 쉰다.
    sleep "${COOLDOWN_SEC:-20}"
  done
done

node "$(winpath "$LOADTEST_DIR/scripts/summarize.mjs")" "$(winpath "$LOADTEST_DIR/results/$STAGE")"
