# 공간 기반 음악 랭킹

기존 `Techeer-2026-2/backend`의 `develop` (`38bbeb0`)에 추가한 P1 기능이다. Java 21, Spring Boot 3.5.3, PostgreSQL을 그대로 사용한다. 랭킹 테이블은 Flyway가 별도 `ranking` 스키마에 생성하며 기존 광고 테이블은 변경하지 않는다.

첨부 설계의 데이터 수집·집계·조회와 이를 확인할 독립 차트 화면을 구현했다. GitHub의 프론트엔드 저장소가 비어 있어 실제 React 앱 연결을 위한 클라이언트 예제를 함께 제공한다. **사용자 확인에 따라 재생 시작 위치를 사용한다.** 위치 유효 시간 30초도 완료 시점이 아닌 시작 시점 기준이다. 시간 버킷은 여전히 **완료 시각** 기준이다. 예를 들어 10:58에 성수에서 시작해 11:02에 다른 곳에서 완주하면, 성수의 11:00~11:59 버킷에 들어간다.

## 빠르게 확인하기

Docker가 실행 중일 때 저장소 루트에서:

```powershell
docker compose -f docker-compose.ranking-demo.yml up --build -d
```

- 화면: <http://localhost:8081/rankings/>
- API 문서: <http://localhost:8081/swagger-ui.html>
- 전체 랭킹: <http://localhost:8081/api/v1/rankings/tracks/global>
- 성수 주변: <http://localhost:8081/api/v1/rankings/tracks/nearby?lat=37.5442&lng=127.0561&limit=50>

가상 곡 50개와 완주 세션 1,000건이 생성된다. GPS는 성수 800건, 강남 120건, 부산 80건으로 분산된다. 위치 갱신 API가 좌표를 수집하고 H3 resolution 8 셀로 변환해 저장하며, 이어지는 재생 시작이 해당 위치의 H3 셀을 고정한다. 곡명·아티스트·청취 기록은 데모 데이터이며 음원 파일은 제공하지 않는다. 같은 시간에 재시작해도 동일 이벤트가 중복 생성되지 않는다. 데모 모드는 시작할 때 직전 한 시간만 채운다. 이후 시간에 재생이 없으면 정상적으로 빈 차트가 생성된다. 새 데모가 필요하면 앱을 재시작한다.

종료: `docker compose -f docker-compose.ranking-demo.yml down`. DB 볼륨은 남으므로 다시 실행할 수 있다.

기존 개발 DB에 연결하려면 기존 실행법을 따른다:

```powershell
docker compose up -d db
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

이 경우 주소는 `http://localhost:8080/rankings/`이다. 가상 기록은 명시적으로 `local,ranking-demo` 프로필을 사용할 때만 생성된다. 운영 DB에서는 데모 프로필을 사용하지 않는다.

## API 연결

모든 날짜 입력은 UTC 또는 시간대 오프셋이 있는 ISO 8601이다. 날짜 예시는 과거 시각이므로 재현 시 원하는 완료 시간대를 재집계해야 한다.

| 메서드·경로 | 용도 |
|---|---|
| `PUT /api/v1/music/tracks/{trackId}` | 기존 음악 검색/카탈로그에서 얻은 곡 정보 등록 |
| `GET /api/v1/music/tracks/{trackId}` | 곡 상세 |
| `POST /api/v1/locations` | 사용자 최신 위치 갱신, 오래된 위치 요청은 덮어쓰지 않음 |
| `POST /api/v1/playback/sessions` | 재생 시작, 위치 스냅샷 고정 |
| `POST /api/v1/playback/events` | 완주·스킵 종료 이벤트 |
| `GET /api/v1/rankings/tracks/nearby?lat=...&lng=...&limit=50` | 저장된 주변 차트 및 fallback |
| `GET /api/v1/rankings/tracks/global?limit=50` | 저장된 전체 차트 |

1. 곡 등록:

```json
{"title":"곡 제목","artistName":"아티스트","previewUrl":null,"externalUrl":null}
```

`trackId`는 최대 128자로 음악 공급자의 ID를 그대로 쓸 수 있다. 링크는 HTTPS만 허용한다. 음원 메타데이터 수집·외부 API 호출은 기존 음악 모듈이 담당한다.

2. 재생 시작:

```json
{
  "sessionId":"01c6c599-a6b0-431e-a751-e8829d88bd62",
  "userId":1001,
  "trackId":"itunes-123",
  "startedAt":"2026-10-03T10:15:00+09:00",
  "location":{"lat":37.5442,"lng":127.0561,"accuracyMeters":25,"recordedAt":"2026-10-03T10:14:50+09:00"}
}
```

`location`은 선택이다. 생략하면 `latest_user_location` 중 시작 시각 이전의 최신 위치를 사용한다. 재생 시작 이벤트가 지연 도착했는데 이미 더 새로운 위치로 덮인 경우에는 과거 위치를 추정하지 않고 전체 차트로 처리한다. 지연 전송을 지원하는 앱은 시작 시 저장한 위치를 직접 포함해야 한다. 위치는 세션에 H3 셀로 고정되므로 이후 GPS 이동의 영향을 받지 않는다.

최신 위치 API의 본문은 `{"userId":1001,"location":{...}}`이다. 시작 시점 대비 30초 초과는 `STALE`, 정확도 100m 초과는 `INACCURATE`, 위치 없음은 `MISSING`이며 모두 전체 차트에만 반영된다. 비정상 좌표, 음수·무한 정확도, 미래 시각은 400이다.

3. 종료:

```json
{
  "eventId":"4f5ed6ed-1b58-48e2-90b4-d1df410bd0e5",
  "sessionId":"01c6c599-a6b0-431e-a751-e8829d88bd62",
  "eventType":"TRACK_COMPLETED",
  "playedRatio":0.95,
  "occurredAt":"2026-10-03T10:18:00+09:00"
}
```

처음 저장하면 201, 같은 ID·동일 본문 재시도는 200, 같은 ID의 다른 본문 또는 한 세션의 두 번째 종료 이벤트는 409다. `counted`는 집계 대상 여부이며 즉시 차트에 반영됐다는 뜻은 아니다. `TRACK_SKIPPED`와 90% 미만 재생은 원본으로 저장되지만 집계 대상에서 제외된다. 새 재생은 새 `sessionId`를 사용한다. 서버는 클라이언트가 보낸 재생 비율을 검증하되 실제 오디오 재생 여부를 증명하지는 않는다.

[playback-client.js](playback-client.js)는 기존 플레이어 콜백에 연결할 ES 모듈 예제다:

```javascript
const session = createListeningSession({ userId, trackId, location });
await session.start(); // 실패하면 동일 객체의 start()로 재시도
// 실제 곡 재생이 끝났을 때:
await session.finish(playedSeconds / durationSeconds);
```

네트워크 재시도에서는 같은 객체/본문을 보관한다. 앱 종료를 넘는 재시도는 앱의 영속 이벤트 큐에 요청 본문을 보관해야 한다. **30초 미리듣기를 전체 곡 완주로 보고하지 않는다.** 제공한 차트 화면의 미리듣기는 랭킹 이벤트를 발생시키지 않는다.

## 집계와 조회 규칙

- `Asia/Seoul` 매시 정각에 `[직전 정각, 현재 정각)`을 집계한다. DB에는 `timestamptz`, 응답에는 `+09:00`을 사용한다.
- H3 resolution 8, 현재 셀과 인접 1링을 사용한다. 이벤트가 없는 중심 셀도 주변 셀의 데이터를 조회할 수 있도록 미리 계산한다.
- 사용자 집합을 합치므로 인접 셀에서 같은 곡을 들은 한 사람은 해당 조회 지역에 한 번만 반영된다.
- 점수는 고유 완주 청취자 수. 동점이면 총 완주 수, 가장 최근 완주 시각, 마지막으로 곡 ID 오름차순이다. 서로 다른 세션의 반복 완주는 총 완주 수에는 포함된다.
- 지역 청취자 20명, 곡별 3명 이상인 Top 50을 저장한다. 개인정보 보호를 위해 전체 차트에도 같은 최소 인원을 적용한다.
- fallback은 `NEARBY → PARENT(r7) → CITY → GLOBAL`. 해당 단계에 노출할 곡이 하나도 없으면 다음 단계로 진행한다.
- 쿼리는 최신 **성공한 배치 한 시간**의 저장 결과만 읽는다. 지역마다 과거 시간으로 되돌아가거나 요청 중 점수를 계산하지 않는다.
- 배치 실행 전은 `PENDING`, 노출 기준 미달은 `INSUFFICIENT_DATA`, 차트 존재는 `READY`. `limit`은 1~50, 좌표·쿼리 오류는 400이다.
- PostgreSQL advisory lock으로 같은 시간의 동시 배치를 막는다. 모든 지역 결과를 단일 트랜잭션으로 교체하고 조회는 repeatable read를 사용한다. 재집계가 실패하면 이전 차트를 유지한다.

원본 이벤트의 `event_id` PK와 `session_id` UNIQUE가 전송 중복을 막는다. 점수의 사용자 중복은 집계 시 집합으로 제거한다. 설계 문서의 사용자별 보조 테이블 세 개를 별도로 중복 저장하지 않고 원본 세션/이벤트로 다시 계산할 수 있게 했다.

## 도시 매핑

H3 자체에는 행정 도시 정보가 없다. `ranking.city_cell(h3_cell, city_id, city_label)`에 **검증한 resolution 8 셀과 도시 매핑**을 적재하면 CITY fallback이 동작한다. 아직 팀의 행정 경계 데이터가 없으므로 임의 사각형을 실제 도시로 간주하지 않았다. 매핑이 없는 위치는 CITY 단계를 건너뛴다. 통합 테스트에서는 별도 테스트 매핑으로 CITY fallback을 검증한다.

```sql
-- 운영 데이터 담당자가 행정 경계에서 생성한 셀 목록을 파일로 준비한 뒤 psql에서 실행:
\copy ranking.city_cell(h3_cell, city_id, city_label) FROM 'city-cells.csv' WITH (FORMAT csv, HEADER true)
```

매핑은 세션 시작 전에 준비한다. 이미 저장한 세션의 지역을 이후 위치 정보로 바꾸지 않는다. 실제 동/구 명칭은 같은 방식으로 확장할 수 있으며, 현재 주변 표시는 `현재 위치 주변`이다.

## 복구와 지연 이벤트

기동 및 정각 실행 시 마지막 성공 시간 이후의 미처리 버킷을 최근 24시간까지 시간 순으로 복구한다. 실패하거나 다른 서버가 같은 시간을 처리 중이면 뒤 시간으로 진행하지 않는다. 신규 DB에서는 직전 한 시간부터 시작한다.

완료 이벤트는 발생 시각 기준으로 저장한다. 이미 공개한 시간의 지연 이벤트는 다음 시간 점수로 이월하지 않는다. 그 시간 차트를 보정하려면 다음 옵션으로 재집계한다:

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=local --ranking.rebuild-hour=2026-10-03T10:00:00+09:00'
```

완료된 정각 버킷만 허용하며 HTTP에 공개된 수동 배치 API는 없다. 24시간보다 오래된 누락도 이 옵션으로 복구한다. `ranking.batch_attempt`에 성공·실패·잠금으로 건너뜀 및 소요 시간이 남고, Micrometer `ranking.batch.duration`과 기존 HTTP 타이머로 실행 시간을 측정할 수 있다. 운영 Actuator 공개 범위는 기존 설정을 유지한다.

## 검증

```powershell
.\gradlew.bat test bootJar
# Docker 없이 계산 규칙만 검증:
.\gradlew.bat test --tests '*RankingCalculatorTest' --tests '*RankingJobsTest'
```

기본 통합 테스트는 기존 Testcontainers PostgreSQL 16을 사용한다. Docker를 쓸 수 없다면 **폐기 가능한 전용 PostgreSQL DB**에 연결한다. 테스트는 테이블을 비우므로 개발/운영 데이터베이스를 지정하지 않는다.

```powershell
$env:TEST_POSTGRES_EXTERNAL='true'
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:55439/ranking_test'
$env:SPRING_DATASOURCE_USERNAME='ranking_test'
$env:SPRING_DATASOURCE_PASSWORD='<테스트 DB 비밀번호>'
.\gradlew.bat test bootJar
```

OneDrive가 `build` 파일을 잠그면 `-PbuildOutputDir=C:/temp/ranking-build`처럼 동기화되지 않는 경로를 지정할 수 있다.

첨부 문서의 인수 조건 9개 외에도 인접 셀 사용자 중복, 재생 중 이동, 30초·100m·90% 경계, 스킵 제외, 미래 값, 동시 이벤트 요청, 부모·도시·전체 fallback, 배치 잠금, 게시 도중 DB 오류 롤백, 배치 복구, 저장 결과만 읽는 API를 테스트한다.

## 팀 앱에 합칠 때

현재 저장소에 음악 사용자 인증/플레이어가 없으므로 `userId`는 양수 내부 ID 계약으로 받는다. 인증 모듈이 들어오면 인증된 사용자 ID로 치환하고 세션 소유권을 확인해야 한다. 곡 등록·위치·재생 수집 API는 그 전까지 신뢰된 개발 클라이언트에서만 사용한다. 실제 전체 곡 재생, 사용자 로그인, 운영용 행정 도시 데이터 적재는 별도 모듈 연결 작업이다.

P1 집계는 한 시간의 이벤트를 메모리에서 계산한다. 큰 부하에서 처리량·힙 사용량을 측정한 뒤 SQL 사전 집계나 워커로 분리한다. 원본 이벤트와 위치 데이터의 보관/삭제 기간은 팀 정책 확정 후 배치를 추가해야 한다.

참고: [팀 백엔드](https://github.com/Techeer-2026-2/backend/tree/develop), [음악 서비스 전환 회의](https://techeer-valley.slack.com/archives/C0BS42JTFM0/p1790218850362889), [H3 Java](https://github.com/uber/h3-java), [H3 gridDisk](https://h3geo.org/docs/api/traversal/). 첨부 문서와 이 대화에서 확인한 시작 위치 기준을 랭킹 기능의 기준으로 사용했다. 저장소의 기존 광고 기능 설계는 별도로 유지한다.
