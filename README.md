# 통근길 플레이리스트 — Backend

## 공간 기반 음악 랭킹

재생 시작 위치를 기준으로 직전 한 시간의 고유 완주 청취자 Top 50을 집계합니다.
H3 주변·상위 셀·도시·전체 fallback, 중복 방지, 재집계 및 차트 화면을 제공합니다.

실행·API·플레이어 연결 방법은 **[음악 랭킹 개발 가이드](docs/RANKING.md)**를 참고하세요.
가상 데이터로 실행: `docker compose -f docker-compose.ranking-demo.yml up --build -d`
→ <http://localhost:8081/rankings/>

통근 정보(노선/소요시간)에 맞춰 음악을 추천하고, 홈 화면에 연령대 기반 배너 광고를 노출하는
음악 + 광고 통합 플랫폼의 **백엔드 API 서버**입니다.

- 조직: [Techeer-2026-2](https://github.com/Techeer-2026-2) · 팀 D (5명)
- 일정: **P1 ~ 10/7** · **P2 ~ 10/16** · P3(고도화) 이후
- 팀 Notion: [2026-하반기-팀프로젝트-d](https://app.notion.com/p/2026-d-3c8dc21d58f080eb89e6c34d9651f266)
  (기능 명세서 / API 명세서 / ERD 는 Notion 이 기준)

---

## 기술 스택

| 구분 | 스택 |
|---|---|
| 언어 / 런타임 | Java 21 |
| 프레임워크 | Spring Boot 3.5.3 (Web MVC, Data JPA, Validation, Actuator) |
| 빌드 | Gradle (Wrapper 포함 — 별도 설치 불필요) |
| DB | PostgreSQL 16 |
| API 문서 | springdoc-openapi 2.8.6 → Swagger UI |
| 테스트 | JUnit 5, Spring Boot Test, Testcontainers |
| 컨테이너 | Docker, Docker Compose |
| CI/CD | GitHub Actions → EC2 (Docker Compose) |
| 코드 리뷰 | CodeRabbit (한국어 자동 리뷰) |

아키텍처는 **모놀리식 + 역할 분리**입니다 (MSA 아님). 계정·캠페인·배너·클릭을 하나의 애플리케이션이 처리합니다.

Redis / Kafka / ClickHouse / S3 는 **Phase 3 부터** 도입합니다. P1 은 PostgreSQL 단일 구조로
시작해 부하를 실측한 뒤 단계적으로 추가합니다.

---

## 빠른 시작

### 1. 환경변수 준비

```bash
cp .env.example .env
# .env 를 열어 POSTGRES_PASSWORD 등을 채운다
```

### 2. 전체 스택 실행

```bash
docker compose up -d
```

| 확인 | URL |
|---|---|
| 헬스 체크 | http://localhost:8080/api/v1/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI 스펙 | http://localhost:8080/v3/api-docs |

### 3. DB 만 컨테이너로 띄우고 IDE 에서 실행

```bash
docker compose up -d db
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 4. 테스트

```bash
./gradlew test
```

> Testcontainers 가 PostgreSQL 컨테이너를 띄우므로 **Docker 가 실행 중이어야** 합니다.
> Docker 없이 빠른 테스트만 돌리려면: `./gradlew test --tests '*HealthControllerTest'`

---

## 프로필

| 프로필 | 용도 | JPA `ddl-auto` |
|---|---|---|
| `local` | 로컬 개발 (기본값) | `update` |
| `test` | 테스트 (Testcontainers) | `create-drop` |
| `prod` | EC2 배포 | `validate` |

---

## 프로젝트 구조

```
backend/
├── src/main/java/com/techeer/backend/
│   ├── BackendApplication.java
│   ├── global/
│   │   ├── config/SwaggerConfig.java
│   │   └── health/HealthController.java
│   └── domain/                      ← 도메인별 패키지를 여기에 추가
├── src/main/resources/
│   ├── application.yml              공통 설정
│   ├── application-local.yml        로컬
│   └── application-prod.yml         배포
├── .github/
│   ├── workflows/ci.yml             PR → 빌드·테스트·Docker 빌드 검증
│   ├── workflows/deploy.yml         main push → EC2 배포
│   ├── pull_request_template.md
│   └── ISSUE_TEMPLATE/
├── nginx/nginx.conf                 리버스 프록시 (배포용)
├── docker-compose.yml               로컬 스택
├── docker-compose.prod.yml          EC2 스택
├── Dockerfile                       멀티스테이지 빌드
├── .coderabbit.yaml                 CodeRabbit 리뷰 규칙
├── CONTRIBUTING.md                  브랜치·커밋·PR·코드 컨벤션
└── docs/ARCHITECTURE.md             시스템 아키텍처 · ERD 통합 문서
```

---

## CI/CD

### CI — `.github/workflows/ci.yml`

`develop` / `main` 으로 PR 이 올라오면 실행됩니다.

1. JDK 21 설정 + Gradle 캐시
2. `compileJava` / `compileTestJava`
3. `test` (Testcontainers 로 PostgreSQL 기동)
4. 테스트 리포트 아티팩트 업로드
5. `bootJar` 빌드
6. **Docker 이미지 빌드 검증** (푸시는 하지 않음)

### CD — `.github/workflows/deploy.yml`

`main` 에 push(merge) 되면 실행됩니다.

1. rsync 로 EC2 에 코드 전송
2. `docker compose -f docker-compose.prod.yml` 로 `stop → build → up`
   (프리티어 1GB 에서 컨테이너 가동 중 빌드하면 OOM `Exit 137` 이 나므로 stop 을 먼저 합니다)
3. `/api/v1/health` 헬스 체크로 배포 검증 — 실패 시 워크플로우 실패 + 로그 수집

### 필요한 GitHub Secrets

| 이름 | 용도 |
|---|---|
| `EC2_HOST` | EC2 퍼블릭 IP 또는 도메인 |
| `EC2_USER` | SSH 계정 (Amazon Linux 는 `ec2-user`, Ubuntu 는 `ubuntu`) |
| `EC2_SSH_KEY` | EC2 접속용 **개인키 전문** (`-----BEGIN ... KEY-----` 포함) |

> iTunes Search API 는 키가 필요 없습니다. `OPENWEATHER_API_KEY` / `KAKAO_REST_API_KEY` 는
> EC2 의 `~/backend/.env` 에 둡니다 (Actions 가 `.env` 를 rsync 에서 제외합니다).

배포 워크플로우는 `production` 환경을 사용합니다. Secrets 가 등록되기 전에는 실패하므로,
EC2 준비 전까지는 `main` 으로 머지하지 않거나 워크플로우를 비활성화해 두세요.

---

## 외부 API

| 용도 | API | 주의사항 |
|---|---|---|
| 음악 검색/재생 | iTunes Search API | 인증 불필요, `country=KR`, **CORS 미지원 → 백엔드 프록시 필수**, 30초 미리듣기만 |
| 날씨 | OpenWeatherMap | 무료 티어(분당 60회 / 월 100만 회) |
| 지도/장소 | 카카오맵 | 일 10만 건. **검색 결과를 DB 에 대량 캐싱/재사용하는 것은 정책상 금지** |

Spotify 는 2026년 2월 정책 변경(개발자 Premium 필수 + 테스트 유저 5명 제한)으로, Deezer 는
한국 미제공으로 각각 제외됐습니다.

---

## 기여

브랜치 전략, 커밋 메시지, PR 규칙, 코드 컨벤션은 **[CONTRIBUTING.md](CONTRIBUTING.md)** 를 읽어주세요.

요약: 이슈 생성 → `feat/#12` 브랜치 → PR to `develop` → 리뷰 승인 → 본인이 Squash merge.
