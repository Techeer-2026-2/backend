---
id: 2026-10-16-context-records-and-harness
title: AI 와 정한 결정을 구역별 짧은 기록으로 남기고 AGENTS.md 와 CI 로 지키게 한다
zone: etc
status: 제안
recorder: kmssss56
ai: Claude
recorded: 2026-10-16
discussed: 2026-10-15~2026-10-16
refs: "#95"
files: AGENTS.md, docs/context/, scripts/check-docs.sh, .github/workflows/docs-check.yml
summary: 대화는 사라지므로 결정과 이유만 dev/design/etc 로 나눈 30줄 이하 기록으로 남기고, 형식은 CI 가 검사한다
---

## 왜
AI 와 대화하며 구현하면 "왜 이렇게 만들었는지"가 대화에만 남고 사라진다. 나중에 고도화하거나 기능을 추가할 때 같은 조사를 처음부터 다시 하게 된다.

## 결정
- 결정과 이유만 `docs/context/` 에 남긴다. 구역은 dev / design / etc, 하위 폴더는 코드 패키지 이름을 따른다
- 읽을 때는 INDEX → 구역 INDEX → 관련 기록 1~2개 순서로만 열어 토큰을 아낀다
- 기록 하나는 본문 30줄 이하. 길어지면 결정을 둘로 나눈다
- 파일 이름에 날짜를 써서(`YYYY-MM-DD-slug.md`) PR 여러 개가 동시에 추가해도 이름이 겹치지 않게 한다
- 기록자(`recorder`)와 AI, 기록일, 대화일을 파일에 적는다. 이 레포는 Squash 머지라 git 이력에는 PR 작성자만 남기 때문이다
- 지침은 `AGENTS.md`(Codex 등이 기본으로 읽음), `CLAUDE.md` 는 `@AGENTS.md` 한 줄로 연결한다
- 형식 검사는 `scripts/check-docs.sh` 를 CI 로 돌린다 (30줄, 필수 항목, INDEX 등록, 키·비밀번호 문자열)

## 한계
- AI 가 `AGENTS.md` 를 읽고 따르는지는 보장되지 않는다. CI 가 보장하는 것은 형식뿐이고, 내용의 정확성과 결정 누락은 PR 리뷰에 의존한다.
- hook(위험 명령 차단 등)은 팀원 작업 방식에 영향이 있어 이번에 넣지 않았다.

## 대안
| 대안 | 기각 이유 |
|---|---|
| 이슈·PR 댓글에만 남김 | 몇 달 뒤 찾기 어렵고 구조가 없다 |
| 결정 하나당 길이 제한 없는 긴 문서 | AI 가 읽는 토큰이 늘고 낡은 내용이 섞인다 |
| 완전 자동 저장 | 틀린 요약이나 비밀이 검토 없이 들어간다 |

## 되돌릴 조건
팀원이 기록을 쓰지도 읽지도 않으면 규칙을 줄이거나 없앤다. 반대로 지켜지지 않는 규칙이 반복되면 CI 검사를 늘린다.
