#!/usr/bin/env bash
# 1초마다 잠금 대기 수와 활성 커넥션 수를 CSV 로 남긴다. 종료(SIGTERM)될 때까지 돈다.
#   bash loadtest/scripts/lock-sampler.sh results/xxx/locks.csv
set -uo pipefail
source "$(dirname "$0")/env.sh"
OUT="${1:?출력 CSV 경로}"
INTERVAL="${SAMPLE_INTERVAL:-1}"

echo "ts,lock_waits,active,idle_in_tx" > "$OUT"
trap 'exit 0' TERM INT
while true; do
  row=$(docker exec "$DB_CONTAINER" psql -U "$PG_USER" -d "$LOADTEST_DB" -tA -F, -c "
    SELECT count(*) FILTER (WHERE wait_event_type = 'Lock'),
           count(*) FILTER (WHERE state = 'active'),
           count(*) FILTER (WHERE state = 'idle in transaction')
    FROM pg_stat_activity WHERE datname = current_database() AND pid <> pg_backend_pid();" 2>/dev/null)
  [ -n "$row" ] && echo "$(date +%s),$row" >> "$OUT"
  sleep "$INTERVAL"
done
