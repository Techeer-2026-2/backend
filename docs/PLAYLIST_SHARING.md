# 플레이리스트 공유 · 현재 셀 청취 인원

> 처음 구현은 coder072 님의 PR #83 입니다. 현재 develop 의 플레이리스트(`playlists`)·회원 식별(`@MemberId`) 구조에 맞춰 옮겼습니다.

## 범위

내 플레이리스트의 공유 링크(토큰)를 만들어 다른 사람에게 전달하면, 받은 사람이 로그인 없이 곡 목록을 조회한다.
앱에서 재생 중일 때 위치와 함께 재생 신호(heartbeat)를 보내면, 조회자의 **현재 H3 셀(해상도 8)** 에서 같은 플레이리스트를 듣는 고유 회원 수를 돌려준다. 이름이나 개별 위치는 노출하지 않는다.

청취 인원은 앱이 보낸 재생 상태를 신뢰한 **POC 수치**다. 서버가 실제 재생을 검증하지 않는다. Redis·Kafka 는 쓰지 않고 PostgreSQL 만 쓴다.

## API

`@MemberId` 는 로그인 토큰(`Authorization: Bearer`)으로, 프론트 전환 전까지는 설정에 따라 `X-Member-Id` 헤더로도 식별한다(README 의 `AUTH_LEGACY_MEMBER_HEADER`). 응답은 다른 플레이리스트 API 와 같이 `data` 로 감싸지 않는다.

| 메서드 | 경로 | 로그인 | 기능 |
|---|---|---|---|
| POST | `/api/v1/playlists/{playlistId}/share-links` | 필요 | 공유 토큰 발급. 이미 공유 중이면 같은 토큰. 곡이 없으면 409 `PLAYLIST_EMPTY` |
| DELETE | `/api/v1/playlists/{playlistId}/share-links` | 필요 | 공유 취소 + 그 플레이리스트의 청취 기록 삭제 |
| GET | `/api/v1/shared/playlists/{shareToken}` | **불필요** | 공유 플레이리스트 조회 |
| PUT | `/api/v1/shared/playlists/{shareToken}/listening` | 필요 | 재생 신호와 현재 위치 전송 |
| DELETE | `/api/v1/shared/playlists/{shareToken}/listening` | 필요 | 재생 종료(반복해도 204) |
| GET | `/api/v1/shared/playlists/{shareToken}/active-listeners?lat=&lng=&recordedAt=` | 필요 | 조회자의 셀에서 듣는 인원 |

내 플레이리스트가 아니거나 없으면 모두 404 `PLAYLIST_NOT_FOUND` 이다(남의 것은 존재 여부도 알려 주지 않는다). 없거나 취소된 토큰도 404 이다.

재생 신호 예시 (`PUT .../listening`):

```json
{"lat":37.555,"lng":126.923,"recordedAt":"2026-10-15T08:00:00Z"}
```

앱은 재생 중 약 **20초마다** 현재 GPS 측정 시각과 함께 보내고, 일시정지·종료 시 `DELETE .../listening` 을 호출한다.
**마지막 신호가 60초 이내**이고 **위치 측정 시각이 30초 이내**인 회원만 집계한다. 둘 중 하나라도 지나면 응답에서 바로 빠지고, 저장 행은 1분 주기 작업이 지운다.
위치 시각이 30초보다 오래됐거나 5초보다 미래이면 400, 이미 저장된 신호보다 오래된 신호(늦게 도착한 요청)도 400 이다.
회원당 활성 행이 하나뿐이라 같은 회원의 반복 신호는 한 명이고, 다른 플레이리스트·셀로 옮기면 이전 쪽에서는 빠진다.

인원 응답 예시:

```json
{"playlistId":12,"h3Cell":"8830e1d8d3fffff","activeListenerCount":3,"asOf":"2026-10-15T08:00:03Z"}
```

## DB (운영 EC2 수동 반영 필요, `ddl-auto: validate`)

```sql
ALTER TABLE playlists ADD COLUMN share_token VARCHAR(36) UNIQUE;

CREATE TABLE playlist_active_listenings (
  member_id bigint PRIMARY KEY,
  playlist_id bigint NOT NULL,
  h3_cell varchar(15) NOT NULL,
  location_recorded_at timestamp(6) with time zone NOT NULL,
  heartbeat_at timestamp(6) with time zone NOT NULL
);
CREATE INDEX idx_playlist_active_listenings_count
  ON playlist_active_listenings (playlist_id, h3_cell, heartbeat_at, location_recorded_at);
```

## 한계

- 위치·재생 신호는 클라이언트가 위조할 수 있다. 재생 인증이나 위치 위변조 방지는 이번 범위가 아니다
- GPS 좌표를 URL 쿼리(`active-listeners`)로 보내므로 프록시 접근 로그의 쿼리 문자열 보관 정책을 확인해야 한다
- H3 셀 경계에서 매우 가까운 사용자가 다른 셀로 나뉠 수 있고, 인원이 적으면 개인을 추정할 수 있다. 공개 서비스 전 최소 인원 임계값·위치 동의·보관 정책이 필요하다
- 플레이리스트를 삭제해도 청취 기록은 즉시 지우지 않는다(조회는 토큰으로만 되므로 집계에 나오지 않고, 만료 정리 작업이 지운다)
- 다중 서버에서 정리 작업이 동시에 돌아도 같은 만료 행을 지울 뿐이라 결과는 같다. 부하 테스트는 하지 않았다
