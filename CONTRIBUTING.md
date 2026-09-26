# 기여 가이드 (백엔드)

팀 Notion 의 [코드컨벤션](https://app.notion.com/p/abedc21d58f083d8a7ca01f5b8b6a89b)과
[Github 명령어/규칙](https://app.notion.com/p/175dc21d58f0832b9108014ffaeada93)을 이 레포 기준으로 정리한 문서입니다.

> ⚠️ Notion 코드컨벤션 페이지의 **백엔드 항목은 Python/FastAPI 기준**으로 작성돼 있습니다
> (스네이크 케이스 변수명, `except:`, 4칸 들여쓰기). 이 레포는 **Spring Boot / Java 21** 이므로
> 아래 Java 컨벤션을 따릅니다. Notion 쪽도 정리가 필요합니다.

---

## 1. 브랜치 전략

| 브랜치 | 역할 | 비고 |
|---|---|---|
| `main` | 배포용 | push 시 EC2 자동 배포. 직접 push 금지 |
| `develop` | 통합 | 모든 기능 PR 의 목적지. 직접 push 금지 |
| `<타입>/#<이슈번호>` | 기능 개발 | 예: `feat/#12`, `fix/#31` |

```bash
# 이슈를 먼저 만들고 → 이슈 번호로 브랜치를 딴다
git fetch origin
git switch develop
git pull
git switch -c feat/#12
```

**`main` / `develop` 에 직접 push 하지 않습니다.** 브랜치 보호 규칙으로 막혀 있습니다.

---

## 2. 커밋 메시지

형식: `<타입>: <변경 내용>` — **명령문, 현재형, 마침표 없음**

```
feat: 캠페인 생성 API 구현          (O)
feat: 캠페인 생성 API 구현했습니다.   (X)
```

커밋 템플릿을 등록하면 타입 목록이 에디터에 뜹니다:

```bash
git config --local commit.template .gitmessage.txt
```

| 타입 | 용도 |
|---|---|
| `feat` | 새로운 기능 추가 또는 기능 업데이트 |
| `fix` | 버그 또는 에러 수정 |
| `style` | 코드 포맷팅, 오타, 이름 수정 |
| `refactor` | 기능은 같고 코드만 개선 |
| `file` | 파일 이동·제거, 파일명 변경 |
| `design` | 디자인, 문장 수정 |
| `comment` | 주석 수정 및 삭제 |
| `chore` | 개발 환경 세팅, 빌드 수정, 패키지 추가, 환경변수 설정 |
| `docs` | 문서 수정 |
| `test` | 테스트 추가·수정 |
| `hotfix` | 치명적 버그 긴급 수정 (리뷰 없이 머지 가능) |

커밋은 **자주, 세부적으로** 남깁니다.

---

## 3. PR 규칙

1. 이슈를 먼저 생성합니다 (`[feat] 캠페인 생성 API 구현` → `#12`).
2. `feat/#12` 브랜치에서 작업하고 push 합니다.
3. PR 을 **`develop` 으로** 올립니다. 제목은 이슈와 같은 형식.
4. PR 템플릿을 채우고, **CI 가 초록불**인지 확인합니다.
5. **팀원 1명 이상의 리뷰 승인** 후 **본인이 머지**합니다.
6. 머지는 **Squash merge** 를 사용합니다 (develop 히스토리를 이슈 단위로 유지).

리뷰는 CodeRabbit 이 자동으로 1차 리뷰를 남깁니다 (한국어). 사람 리뷰가 최종 판단입니다.

### push 전 체크

```bash
./gradlew build          # 빌드 + 테스트 통과 확인
git switch develop && git pull
git switch feat/#12 && git merge develop   # 충돌은 로컬에서 해결
```

웹 에디터에서 충돌을 해결하면 히스토리가 지저분해지므로 로컬에서 처리합니다.

---

## 4. Java 코드 컨벤션

- 들여쓰기는 **공백 4칸**.
- 클래스는 `PascalCase`, 메서드·필드·지역변수는 `camelCase`, 상수는 `ALL_CAPS_WITH_UNDERSCORE`.
- 이름은 기능을 알 수 있게 명확히 (`list` 보다 `activeCampaigns`).
- 한 줄로 된 `if` / `for` / `while` 을 쓰지 않습니다. 중괄호를 항상 씁니다.
- 예외를 `catch (Exception e)` 로 뭉뚱그리지 않고 구체적으로 잡습니다.
- 임포트는 와일드카드(`import java.util.*`)를 쓰지 않습니다.
- 실행되는 코드만 남기고 커밋합니다 (주석 처리된 코드, 미사용 임포트 제거).

### 레이어 규칙

```
controller  →  service  →  repository
```

- **Controller** 에 비즈니스 로직을 두지 않습니다. 요청 검증과 위임만 합니다.
- **Entity 를 API 응답으로 직접 반환하지 않습니다.** 반드시 DTO 로 감쌉니다.
- `@Transactional` 은 Service 에 두고, 범위를 필요 이상으로 넓히지 않습니다.
- 조회 전용 메서드에는 `@Transactional(readOnly = true)` 를 붙입니다.

### 패키지 구조

```
com.techeer.backend
├── global/            공통 설정·예외·응답 포맷
│   ├── config/        Swagger, Web, JPA 설정
│   └── health/        헬스 체크
└── domain/            도메인별 패키지 (아래 형태로 추가)
    └── campaign/
        ├── controller/
        ├── service/
        ├── repository/
        ├── entity/
        └── dto/
```

### 시크릿

API 키, DB 비밀번호를 **코드에 하드코딩하지 않습니다.** `application.yml` 에서 환경변수로 읽고,
값은 로컬 `.env` 와 GitHub Secrets 에 둡니다. 새 환경변수를 추가하면 `.env.example` 도 같이 갱신합니다.

---

## 5. 로컬 실행

```bash
cp .env.example .env     # 값 채우기
docker compose up -d     # PostgreSQL + API 서버
curl localhost:8080/api/v1/health
open http://localhost:8080/swagger-ui.html
```

DB 만 컨테이너로 띄우고 애플리케이션은 IDE 에서 실행하려면:

```bash
docker compose up -d db
./gradlew bootRun --args='--spring.profiles.active=local'
```

테스트는 Testcontainers 로 PostgreSQL 을 띄우므로 **Docker 가 실행 중이어야** 합니다.
