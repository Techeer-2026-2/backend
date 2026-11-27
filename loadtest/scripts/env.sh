# 스크립트 공통 값. 다른 스크립트가 source 한다.
DB_CONTAINER="${DB_CONTAINER:-cp-db}"
PG_USER="${POSTGRES_USER:-cp_user}"
LOADTEST_DB="${LOADTEST_DB:-commute_playlist_loadtest}"
BASE_URL="${BASE_URL:-http://localhost:8080}"

LOADTEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$(cd "$LOADTEST_DIR/.." && pwd)"

# Git Bash 가 /로 시작하는 인자를 Windows 경로로 바꾸지 않게 한다.
export MSYS_NO_PATHCONV=1

psql_lt() {
  docker exec -i "$DB_CONTAINER" psql -U "$PG_USER" -d "$LOADTEST_DB" -v ON_ERROR_STOP=1 "$@"
}

# winget 으로 막 설치해 PATH 가 아직 갱신되지 않은 셸에서도 k6 를 찾는다.
if ! command -v k6 >/dev/null 2>&1 && [ -x "/c/Program Files/k6/k6.exe" ]; then
  export PATH="/c/Program Files/k6:$PATH"
fi

# k6.exe·node.exe 같은 Windows 프로그램에 넘길 경로. Git Bash 의 /c/... 를 C:/... 로 바꾼다(다른 OS 는 그대로).
winpath() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else echo "$1"; fi
}
