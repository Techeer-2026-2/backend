---
id: 2026-10-16-playlist-share-port-from-pr83
title: 공유·청취 인원은 PR #83 의 자체 플레이리스트를 쓰지 않고 develop 의 Playlist 위에 옮겨 구현한다
zone: design
status: 제안
recorder: kmssss56
ai: Claude
recorded: 2026-10-16
discussed: 2026-10-16
refs: "#82, #83"
files: domain/playlist/service/PlaylistShareService.java, domain/playlist/entity/ActiveListening.java
summary: PR #83 은 옛 feat/auth 위에 자체 플레이리스트·회원·보안 설정을 새로 만들어 develop 과 충돌해서, 공유 토큰과 청취 기록만 현재 Playlist·@MemberId 위에 다시 구현한다
---

## 왜
#83 은 develop 이 아니라 옛 `feat/auth` 위에 만들어졌다. 별도 `MusicPlaylist`·`music_users`·Spring Security 설정과 `POST/GET /playlists` 를 새로 정의해, develop 과 합치면 20개 넘는 파일이 충돌하고 같은 주소가 두 번 매핑된다. 회원 기능은 이미 #72~#74 로 머지됐다 (#79·#80 과 겹침).

## 결정
- 공유는 `playlists.share_token`(UUID, 공유 중에만 값)으로 한다. 청취 기록은 새 테이블 `playlist_active_listenings`(회원당 1행)다
- 주소는 API 명세서에 맞춘다: `/playlists/{id}/share-links`, `/shared/playlists/{shareToken}`. 청취 신호·인원은 `/shared/playlists/{shareToken}/listening`, `/active-listeners`
- 응답은 `data` 로 감싸지 않는다 (플레이리스트 API 와 같게). 회원은 `@MemberId` 로 식별한다
- 재생 신호는 DB upsert 한 문장으로 저장해, 같은 회원의 첫 신호가 동시에 와도 실패하지 않고 늦게 도착한 오래된 신호는 거절한다
- 공유 토큰은 행을 잠그고 만들어 동시 요청에도 하나만 만든다
- PR #83 의 "공유 중인 플레이리스트는 곡을 비울 수 없다" 규칙은 옮기지 않았다 (곡 삭제 API 가 이미 따로 있음)

## 대안
| 대안 | 기각 이유 |
|---|---|
| #83 을 develop 에 맞춰 그대로 합침 | 자체 플레이리스트·회원 테이블이 develop 의 `Playlist`·`users` 와 중복되고 충돌이 많다 |
| 청취 기록을 Flyway(ranking 스키마)로 만들기 | Flyway 는 JPA 가 만드는 `playlists` 보다 먼저 돌아서 참조할 수 없고, 다른 음악 앱 테이블과 관리 방식이 달라진다 |

## 한계
청취 인원은 앱이 보낸 신호를 믿는 POC 수치다. 위치 동의·최소 인원 임계값은 정하지 않았다. 부하 테스트는 하지 않았다.

## 되돌릴 조건
공개 서비스 전에 위치 동의·최소 인원 임계값을 정하고, 곡 비우기 제한이 필요하면 곡 삭제 API 쪽에 넣는다.
