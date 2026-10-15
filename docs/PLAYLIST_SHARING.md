# 플레이리스트 공유 · 현재 셀 청취 인원

## 범위

로그인한 사용자가 곡 목록을 골라 플레이리스트를 만들고, 공유 코드를 발급해 다른 사람에게 전달한다. 공유받은 사용자는 링크의 플레이리스트를 조회할 수 있다. 앱에서 실제 재생 중일 때 위치와 함께 heartbeat를 보내면, 조회자의 **현재 H3 셀(해상도 8)** 안에서 같은 플레이리스트를 듣는 고유 사용자 수를 반환한다. 사용자 이름이나 개별 위치는 노출하지 않는다.

이 기능은 기존 PostgreSQL을 사용하며 Redis·Kafka는 추가하지 않는다. 음악 재생 자체를 서버가 검증하는 기능은 아니므로, 청취 인원은 앱이 보낸 재생 상태를 신뢰한 **POC 수치**다. UI 연결은 이 저장소 범위 밖이다.

## API

응답은 `{"data": ...}` 형식이다. 별도 표기가 없으면 `Authorization: Bearer <accessToken>`이 필요하다. 계정 생성·로그인은 [계정 가이드](ACCOUNT_AUTH.md)를 참고한다. 곡 ID는 `ranking.track`에 등록되어 있어야 한다.

| 메서드 | 경로 | 기능 |
|---|---|---|
| POST | `/api/v1/playlists` | 내 플리 생성 (`title`, `description?`, `trackIds`) |
| GET | `/api/v1/playlists` | 내 플리 목록 |
| GET | `/api/v1/playlists/{playlistId}` | 내 플리 상세 |
| PUT | `/api/v1/playlists/{playlistId}/tracks` | 곡 목록 전체 교체 (`trackIds`) |
| POST | `/api/v1/playlists/{playlistId}/share` | 공유 코드 발급; 재호출 시 같은 코드 반환 |
| DELETE | `/api/v1/playlists/{playlistId}/share` | 공유 취소 및 현재 청취 기록 삭제 |
| GET | `/api/v1/shared/playlists/{shareCode}` | 공유 플리 상세; 이 API만 로그인 불필요 |
| PUT | `/api/v1/shared/playlists/{shareCode}/listening` | 재생 heartbeat와 현재 위치 전송 |
| DELETE | `/api/v1/shared/playlists/{shareCode}/listening` | 재생 종료 |
| GET | `/api/v1/shared/playlists/{shareCode}/active-listeners?lat=...&lng=...&recordedAt=...` | 조회자의 셀에서 현재 듣는 인원 |

생성 예시:

```json
{"title":"퇴근길 플리","description":"지하철에서 듣기","trackIds":["song-a","song-b"]}
```

공유 발급 응답의 `apiPath`는 **API 경로**다. 프론트엔드는 `shareCode`를 앱의 공유 URL에 넣어 전달하면 된다. 소유자만 곡 목록을 수정하거나 공유를 취소할 수 있다. 공유 중인 플리는 곡 목록을 비울 수 없다.

재생 heartbeat 예시 (`PUT .../{shareCode}/listening`):

```json
{"lat":37.555,"lng":126.923,"recordedAt":"2026-10-15T08:00:00Z"}
```

앱은 재생 중 약 **20초마다** 현재 GPS 측정 시각과 함께 보내고, 일시정지·종료 시 `DELETE .../listening`을 호출한다. 마지막 heartbeat가 **60초 이내**이고 위치 측정 시각이 **30초 이내**인 사용자만 집계한다. 둘 중 하나라도 만료되면 응답에서 즉시 제외되며, 저장 행은 1분 주기 작업으로 정리된다. 오래된 위치(30초 초과) 또는 미래 시각(5초 초과)은 거절한다. 사용자당 활성 행이 하나뿐이므로 같은 사용자의 중복 heartbeat는 한 명으로 계산되고, 다른 플리로 옮기면 이전 플리에서는 빠진다.

인원 응답 예시:

```json
{"data":{"playlistId":12,"h3Cell":"8830e1d8d3fffff","activeListenerCount":3,"asOf":"2026-10-15T08:00:03Z"}}
```

조회자의 `lat/lng/recordedAt`도 현재 위치여야 한다. GPS 좌표를 URL 쿼리로 보내므로 프록시 접근 로그의 쿼리 문자열 보관 정책을 확인해야 한다. 위치·재생 신호는 클라이언트가 위조할 수 있으며, 재생 인증이나 위치 위변조 방지는 이번 범위가 아니다. H3 셀 경계에서 매우 가까운 사용자가 다른 셀로 나뉠 수 있고, 소수 인원 수치로 개인을 추정할 가능성도 있다. 공개 서비스 전 최소 인원 임계값·위치 동의·보관 정책을 별도로 정해야 한다.

## 실행·검증

PostgreSQL을 사용하는 기존 로컬 환경에서 실행하면 Flyway V3가 테이블을 생성한다. 기본 설정은 `localhost:5432/commute_playlist`이며 자세한 실행 방법은 루트 README에 있다. Swagger는 `http://localhost:8080/swagger-ui.html`에서 확인한다.

Docker 없이 이번 기능의 H2 통합 테스트만 실행할 수 있다:

```powershell
.\gradlew.bat test --tests com.techeer.backend.domain.playlist.PlaylistApiIntegrationTest
```

테스트는 소유자 권한, 공유 조회, 동일 사용자의 중복 heartbeat 방지, 셀 이동, 재생 종료, 공유 취소, 오래된 위치와 청취 만료를 검증한다. 실제 PostgreSQL 마이그레이션·다중 서버 동시 heartbeat·부하 테스트는 별도 환경에서 확인해야 한다.
