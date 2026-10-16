#!/usr/bin/env bash
# check-docs.sh 가 잘못된 기록을 정말 막는지 확인한다. 사용법: bash scripts/check-docs.test.sh
set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
CHECK="$HERE/check-docs.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
failed=0

# 올바른 기록 하나가 들어 있는 최소 레포를 만든다.
make_repo() {
  local root="$WORK/$1"
  mkdir -p "$root/docs/context/dev/music" "$root/docs/context/design" "$root/docs/context/etc"
  cat > "$root/docs/context/INDEX.md" <<'EOF'
# 구역
| dev | [dev](dev/INDEX.md) |
| design | [design](design/INDEX.md) |
| etc | [etc](etc/INDEX.md) |
EOF
  printf '# design\n| id |\n|---|\n' > "$root/docs/context/design/INDEX.md"
  printf '# etc\n| id |\n|---|\n' > "$root/docs/context/etc/INDEX.md"
  cat > "$root/docs/context/dev/INDEX.md" <<'EOF'
# dev
| id | 상태 | 요약 |
|---|---|---|
| [2026-10-16-sample](music/2026-10-16-sample.md) | 제안 | 샘플 |
EOF
  cat > "$root/docs/context/dev/music/2026-10-16-sample.md" <<'EOF'
---
id: 2026-10-16-sample
title: 샘플
zone: dev
status: 제안
recorder: someone
ai: Claude
recorded: 2026-10-16
discussed: 2026-10-15~2026-10-16
refs: "#1"
summary: 샘플 기록
---

## 왜
이유

## 결정
내용
EOF
  echo "$root"
}

expect_pass() {
  local name="$1" root="$2"
  if bash "$CHECK" "$root" > "$WORK/out.txt" 2>&1; then
    echo "  ok   $name"
  else
    echo "  FAIL $name (통과해야 하는데 실패함)"; cat "$WORK/out.txt"; failed=$((failed + 1))
  fi
}

expect_fail() {
  local name="$1" root="$2" needle="$3"
  if bash "$CHECK" "$root" > "$WORK/out.txt" 2>&1; then
    echo "  FAIL $name (실패해야 하는데 통과함)"; failed=$((failed + 1))
  elif grep -qF -- "$needle" "$WORK/out.txt"; then
    echo "  ok   $name"
  else
    echo "  FAIL $name (실패했지만 기대한 메시지 '$needle' 가 없음)"; cat "$WORK/out.txt"; failed=$((failed + 1))
  fi
}

REC="docs/context/dev/music/2026-10-16-sample.md"

r=$(make_repo ok);                                  expect_pass "올바른 기록은 통과" "$r"

r=$(make_repo long)
{ for i in $(seq 1 31); do echo "줄 $i"; done; } >> "$r/$REC"
expect_fail "본문 30줄 초과" "$r" "본문이"

r=$(make_repo blank)
{ for i in $(seq 1 40); do echo ""; done; } >> "$r/$REC"
expect_pass "빈 줄은 줄 수에 세지 않음" "$r"

r=$(make_repo norecorder)
grep -v '^recorder:' "$r/$REC" > "$r/x" && mv "$r/x" "$r/$REC"
expect_fail "기록자 누락" "$r" "'recorder'"

r=$(make_repo nodate)
sed 's/^recorded: .*/recorded: 어제/' "$r/$REC" > "$r/x" && mv "$r/x" "$r/$REC"
expect_fail "날짜 형식 오류" "$r" "recorded"

r=$(make_repo zone)
sed 's/^zone: dev/zone: design/' "$r/$REC" > "$r/x" && mv "$r/x" "$r/$REC"
expect_fail "zone 과 폴더 불일치" "$r" "폴더 위치"

r=$(make_repo badzone)
sed 's/^zone: dev/zone: misc/' "$r/$REC" > "$r/x" && mv "$r/x" "$r/$REC"
expect_fail "허용되지 않은 zone" "$r" "zone 은"

r=$(make_repo status)
sed 's/^status: 제안/status: 진행중/' "$r/$REC" > "$r/x" && mv "$r/x" "$r/$REC"
expect_fail "허용되지 않은 status" "$r" "status"

r=$(make_repo idname)
sed 's/^id: .*/id: 2026-10-16-other/' "$r/$REC" > "$r/x" && mv "$r/x" "$r/$REC"
expect_fail "id 와 파일 이름 불일치" "$r" "파일 이름"

r=$(make_repo notindexed)
printf '# dev\n| id |\n|---|\n' > "$r/docs/context/dev/INDEX.md"
expect_fail "INDEX 에 없는 기록" "$r" "등록되지 않았다"

r=$(make_repo deadlink)
sed 's#music/2026-10-16-sample.md#music/nothing.md#' "$r/docs/context/dev/INDEX.md" > "$r/x" && mv "$r/x" "$r/docs/context/dev/INDEX.md"
expect_fail "INDEX 링크가 없는 파일을 가리킴" "$r" "가리키는 파일이 없다"

r=$(make_repo longindex)
{ for i in $(seq 1 41); do echo "행 $i"; done; } >> "$r/docs/context/dev/INDEX.md"
expect_fail "INDEX 40줄 초과" "$r" "INDEX 로 나눈다"

r=$(make_repo similarid)
sed 's#\[2026-10-16-sample\](music/2026-10-16-sample.md)#[2026-10-16-sample-v2](music/2026-10-16-sample-v2.md)#' "$r/docs/context/dev/INDEX.md" > "$r/x" && mv "$r/x" "$r/docs/context/dev/INDEX.md"
cp "$r/$REC" "$r/docs/context/dev/music/2026-10-16-sample-v2.md"
sed -i.bak 's/^id: .*/id: 2026-10-16-sample-v2/' "$r/docs/context/dev/music/2026-10-16-sample-v2.md" && rm -f "$r/docs/context/dev/music/2026-10-16-sample-v2.md.bak"
expect_fail "다른 id 의 일부만 INDEX 에 있으면 등록 안 된 것" "$r" "2026-10-16-sample](경로)"

r=$(make_repo wronglink)
sed 's#(music/2026-10-16-sample.md)#(music/other.md)#' "$r/docs/context/dev/INDEX.md" > "$r/x" && mv "$r/x" "$r/docs/context/dev/INDEX.md"
touch "$r/docs/context/dev/music/other.md"
expect_fail "id 는 있지만 링크가 다른 파일을 가리킴" "$r" "등록되지 않았다"

r=$(make_repo shortpw)
echo "password: hunter2" >> "$r/$REC"
expect_fail "짧은 password 값" "$r" "키·비밀번호"

r=$(make_repo pwdkorean)
echo "password: 없음 (비밀번호는 적지 않는다)" >> "$r/$REC"
expect_pass "한글 설명이 붙은 password 는 통과" "$r"

r=$(make_repo nofm)
sed '1d' "$r/$REC" > "$r/x" && mv "$r/x" "$r/$REC"
expect_fail "--- 블록 없음" "$r" "--- 블록"

r=$(make_repo awskey)
echo "키는 AKIAABCDEFGHIJKLMNOP 이다" >> "$r/$REC"
expect_fail "AWS 키 형태 문자열" "$r" "키·비밀번호"

r=$(make_repo password)
echo "password: hunter2hunter2hunter2" >> "$r/$REC"
expect_fail "password: 값" "$r" "키·비밀번호"

r=$(make_repo quotedhash)
sed 's/^refs: .*/refs: "#92"   /' "$r/$REC" > "$r/x" && mv "$r/x" "$r/$REC"
expect_pass "따옴표 안의 # 는 주석이 아님" "$r"

r=$(make_repo comment)
sed 's/^status: 제안/status: 제안          # 제안 | 확정/' "$r/$REC" > "$r/x" && mv "$r/x" "$r/$REC"
expect_pass "값 뒤의 # 주석은 무시" "$r"

if [ "$failed" -gt 0 ]; then
  echo "테스트 실패 ${failed}건"
  exit 1
fi
echo "✅ check-docs 테스트 모두 통과"
