#!/usr/bin/env bash
# 부하 테스트 환경을 처음 한 번 준비한다. backend 폴더에서 실행:
#   bash loadtest/scripts/setup.sh
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$BACKEND_DIR"

COMPOSE=(docker compose -f docker-compose.yml -f loadtest/docker-compose.loadtest.yml)

echo "[1/4] DB 기동 (pg_stat_statements 로드)"
"${COMPOSE[@]}" up -d db
until docker exec "$DB_CONTAINER" pg_isready -U "$PG_USER" >/dev/null 2>&1; do sleep 1; done

echo "[2/4] 부하 테스트 DB($LOADTEST_DB) 생성"
if ! docker exec "$DB_CONTAINER" psql -U "$PG_USER" -d postgres -tAc \
    "SELECT 1 FROM pg_database WHERE datname = '$LOADTEST_DB'" | grep -q 1; then
  docker exec "$DB_CONTAINER" psql -U "$PG_USER" -d postgres -c "CREATE DATABASE $LOADTEST_DB"
fi
psql_lt -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements; CREATE EXTENSION IF NOT EXISTS pgcrypto;"

echo "[3/4] 백엔드·Prometheus·Grafana 기동 (백엔드가 테이블을 만든다)"
"${COMPOSE[@]}" up -d --build
echo -n "백엔드 대기"
until curl -sf "$BASE_URL/actuator/health" >/dev/null; do echo -n "."; sleep 2; done
echo

echo "[4/4] 시드 데이터 넣기"
psql_lt < "$LOADTEST_DIR/seed/reset.sql"
psql_lt < "$LOADTEST_DIR/seed/seed.sql"

echo
echo "준비 완료"
echo "  Prometheus: http://localhost:9090/targets (backend, postgres 가 UP 인지 확인)"
echo "  Grafana:    http://localhost:3000"
echo "  측정:       bash loadtest/scripts/run-scenario.sh A 1"
