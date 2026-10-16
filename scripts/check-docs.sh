#!/usr/bin/env bash
# docs/context 기록 점검. 사용법: bash scripts/check-docs.sh [레포 루트 경로(기본 .)]
# 규칙은 docs/context/README.md 에 있다. 하나라도 어기면 종료 코드 1.
set -u

ROOT="${1:-.}"
CTX="$ROOT/docs/context"
MAX_BODY=30      # 본문(맨 위 --- 블록 제외, 빈 줄 제외) 줄 수
MAX_INDEX=40     # INDEX.md 줄 수
REQUIRED="id title zone status recorder recorded discussed refs summary"
errors=0

fail() {
  echo "❌ $1"
  errors=$((errors + 1))
}

if [ ! -d "$CTX" ]; then
  echo "❌ $CTX 가 없다"
  exit 1
fi

# 맨 위 --- 블록에서 키의 값을 읽는다. "따옴표 값"은 따옴표 안쪽, 그 밖은 뒤의 '  # 주석'을 뗀다.
fm_get() {
  awk -v k="$2" '
    /^---[ \t]*$/ { n++; next }
    n == 1 {
      i = index($0, ":")
      if (i > 0 && substr($0, 1, i - 1) == k) {
        v = substr($0, i + 1)
        sub(/^[ \t]+/, "", v)
        if (substr(v, 1, 1) == "\"") {
          v = substr(v, 2)
          j = index(v, "\"")
          if (j > 0) { v = substr(v, 1, j - 1) }
        } else {
          sub(/[ \t]+#.*$/, "", v)
          sub(/[ \t]+$/, "", v)
        }
        print v
        exit
      }
    }
    n >= 2 { exit }
  ' "$1"
}

# 본문의 빈 줄이 아닌 줄 수
body_lines() {
  awk '/^---[ \t]*$/ { n++; next } n >= 2 && $0 !~ /^[ \t]*$/ { c++ } END { print c + 0 }' "$1"
}

DATE_RE='^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
DISCUSSED_RE='^[0-9]{4}-[0-9]{2}-[0-9]{2}(~[0-9]{4}-[0-9]{2}-[0-9]{2})?$'
ID_RE='^[0-9]{4}-[0-9]{2}-[0-9]{2}-[a-z0-9]+(-[a-z0-9]+)*$'

# ── 기록 파일 ──────────────────────────────────────────────
records=$(find "$CTX" -type f -name '*.md' ! -name INDEX.md ! -name README.md ! -name TEMPLATE.md | sort)
count=0
for f in $records; do
  count=$((count + 1))
  rel="${f#"$ROOT"/}"

  if [ "$(head -n 1 "$f")" != "---" ]; then
    fail "$rel: 맨 위에 --- 블록이 없다"
    continue
  fi
  if [ "$(grep -c '^---[[:space:]]*$' "$f")" -lt 2 ]; then
    fail "$rel: --- 블록이 닫히지 않았다"
    continue
  fi

  for key in $REQUIRED; do
    if [ -z "$(fm_get "$f" "$key")" ]; then
      fail "$rel: 필수 항목 '$key' 가 없거나 비었다"
    fi
  done

  id=$(fm_get "$f" id)
  stem=$(basename "$f" .md)
  [ "$id" = "$stem" ] || fail "$rel: id('$id')가 파일 이름('$stem')과 다르다"
  echo "$id" | grep -Eq "$ID_RE" || fail "$rel: id 는 YYYY-MM-DD-영문-slug 형식이어야 한다 ('$id')"

  zone=$(fm_get "$f" zone)
  case "$zone" in
    dev|design|etc) ;;
    *) fail "$rel: zone 은 dev | design | etc 중 하나여야 한다 ('$zone')" ;;
  esac
  case "$f" in
    "$CTX/$zone/"*) ;;
    *) fail "$rel: zone('$zone')과 폴더 위치가 다르다" ;;
  esac

  status=$(fm_get "$f" status)
  case "$status" in
    제안|확정|폐기|대체됨) ;;
    *) fail "$rel: status 는 제안 | 확정 | 폐기 | 대체됨 중 하나여야 한다 ('$status')" ;;
  esac

  echo "$(fm_get "$f" recorded)" | grep -Eq "$DATE_RE" || fail "$rel: recorded 는 YYYY-MM-DD 형식이어야 한다"
  echo "$(fm_get "$f" discussed)" | grep -Eq "$DISCUSSED_RE" || fail "$rel: discussed 는 YYYY-MM-DD 또는 YYYY-MM-DD~YYYY-MM-DD 형식이어야 한다"

  lines=$(body_lines "$f")
  if [ "$lines" -gt "$MAX_BODY" ]; then
    fail "$rel: 본문이 ${lines}줄이다 (최대 ${MAX_BODY}줄). 결정을 둘로 나눈다"
  fi

  # 이 기록으로 가는 링크 "[id](상대경로)" 가 구역 INDEX 중 하나에 있어야 한다. id 가 다른 id 의 일부여도 속지 않는다.
  if [ -n "$id" ] && [ -d "$CTX/$zone" ]; then
    registered=0
    for idx in $(find "$CTX/$zone" -type f -name INDEX.md); do
      idx_dir=$(dirname "$idx")
      case "$f" in
        "$idx_dir"/*)
          if grep -qF -- "[$id](${f#"$idx_dir"/})" "$idx"; then
            registered=1
          fi
          ;;
      esac
    done
    [ "$registered" -eq 1 ] || fail "$rel: $zone 구역의 INDEX.md 에 '[$id](경로)' 링크로 등록되지 않았다"
  fi
done

# ── INDEX 파일 ─────────────────────────────────────────────
for idx in $(find "$CTX" -type f -name INDEX.md | sort); do
  rel="${idx#"$ROOT"/}"
  total=$(wc -l < "$idx" | tr -d ' ')
  if [ "$total" -gt "$MAX_INDEX" ]; then
    fail "$rel: ${total}줄이다 (최대 ${MAX_INDEX}줄). 하위 폴더 INDEX 로 나눈다"
  fi
  dir=$(dirname "$idx")
  for link in $(grep -o '](\([^)]*\.md\))' "$idx" | sed 's/^](//; s/)$//'); do
    case "$link" in
      http*) continue ;;
    esac
    [ -f "$dir/$link" ] || fail "$rel: 링크 '$link' 가 가리키는 파일이 없다"
  done
done

# ── 키·비밀번호로 보이는 문자열 ────────────────────────────
scan_targets="$CTX"
[ -f "$ROOT/AGENTS.md" ] && scan_targets="$scan_targets $ROOT/AGENTS.md"
secret_hits=$(grep -rnE --include='*.md' \
  -e 'AKIA[0-9A-Z]{16}' \
  -e 'gh[pousr]_[A-Za-z0-9]{30,}' \
  -e 'sk-[A-Za-z0-9]{20,}' \
  -e '-----BEGIN [A-Z ]*PRIVATE KEY-----' \
  $scan_targets 2>/dev/null)
# 키·토큰은 긴 값만, 비밀번호 계열은 짧은 값(영문·숫자·기호 4자 이상)도 잡는다. 한글 설명("없음")은 잡지 않는다.
secret_hits2=$(grep -rniE --include='*.md' \
  -e '(api[_-]?key|token|app[_-]?key)[A-Za-z_-]*[[:space:]]*[:=][[:space:]]*["'"'"']?[A-Za-z0-9/+_-]{12,}' \
  -e '(secret|password|passwd|pwd)[A-Za-z_-]*[[:space:]]*[:=][[:space:]]*["'"'"']?[A-Za-z0-9/+_.!@#$%^&*-]{4,}' \
  $scan_targets 2>/dev/null)
if [ -n "$secret_hits" ] || [ -n "$secret_hits2" ]; then
  fail "키·비밀번호로 보이는 문자열이 있다 (값은 출력하지 않는다)"
  { echo "$secret_hits"; echo "$secret_hits2"; } | grep . | cut -d: -f1,2 | sed 's/^/     /'
fi

if [ "$errors" -gt 0 ]; then
  echo "점검 실패: ${errors}건 (기록 ${count}개 검사)"
  exit 1
fi
echo "✅ 점검 통과 (기록 ${count}개)"
