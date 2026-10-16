# AGENTS.md — AI 코딩 도구용 지침

통근길 플레이리스트 백엔드. Spring Boot 3.5.3 / Java 21 / PostgreSQL.
사람용 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md) 가 기준이고, 이 파일은 AI 가 작업 전에 알아야 할 것만 짧게 모았다.

## 자주 쓰는 명령
- 테스트: `./gradlew test` (Testcontainers 가 실제 PostgreSQL 을 띄우므로 Docker 필요)
- 로컬 DB: `docker compose up -d db`
- 기록 점검: `bash scripts/check-docs.sh`

## 코드 규칙
- 패키지: `domain/<이름>/{controller,service,repository,entity,dto}`
- Entity 를 응답으로 직접 반환하지 않는다. DTO 로 감싼다
- `@Transactional` 은 Service 에, 조회는 `readOnly = true`
- Entity 는 setter 없이 `create`, `changeXxx` 같은 의도가 드러나는 메서드를 쓴다. 공통 `BaseEntity`(soft delete)
- 시각은 `LocalDateTime.now(clock)`
- 에러는 `BusinessException(ErrorCode.X)`. `ErrorCode` 는 CONTRIBUTING 의 규칙(도메인 접두사, 자기 구역 맨 위에 추가)을 따른다
- 멱등·unique·동시성은 H2 가 아니라 실제 PostgreSQL(Testcontainers)로 테스트한다
- 키·비밀번호를 코드에 쓰지 않는다. 새 환경변수는 `.env.example` 에도 추가한다

## Git
- 이슈를 먼저 만들고 브랜치는 `<타입>/#<이슈번호>`, 커밋은 `<타입>: <명령형 내용>`(마침표 없음)
- PR 은 `develop` 으로, Squash merge. `main`/`develop` 에 직접 push 하지 않는다
- PR 전에 최신 `develop` 을 합친 결과로 `./gradlew test` 를 돌린다 (같은 이름 상수가 생기면 충돌 표시 없이 컴파일이 깨진다)

## 작업 기록 (`docs/context/`) — 필수

설계·구현 결정이 나오는 대화를 하면 이 규칙을 따른다. 자세한 규칙은 [docs/context/README.md](docs/context/README.md).

1. **작업을 시작하기 전에** `docs/context/INDEX.md` 를 읽고, 관련 구역의 `INDEX.md` 에서 관련 기록 1~2개만 연다. 전부 읽지 않는다
2. 기록과 코드가 다르면 **코드가 사실**이다. 기록을 `대체됨`으로 바꾸는 새 기록을 제안한다
3. 결정이 나면 `docs/context/TEMPLATE.md` 로 **기록 초안**을 만들고 구역 `INDEX.md` 에 한 줄 추가한다. 사람(`recorder`)이 확인한다
4. 구역은 `dev`(구현 방식·버그·실험) / `design`(DB·API 설계) / `etc`(운영·팀 합의). 본문은 빈 줄 제외 30줄 이하, 길면 결정을 나눈다
5. PR 을 쓸 때 이 PR 에서 나온 결정의 기록 `id` 를 PR 설명에 적는다
6. 기록하지 않는다: 잡담, 시행착오 과정, 코드를 읽으면 아는 내용, **키·비밀번호·개인정보**
7. 확인하지 않은 것은 확인했다고 쓰지 않는다. 논의하지 않은 대안을 지어내지 않는다

기록을 추가·수정하면 `bash scripts/check-docs.sh` 가 통과하는지 확인한다 (CI 도 같은 검사를 한다).
