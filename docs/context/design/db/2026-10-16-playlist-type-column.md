---
id: 2026-10-16-playlist-type-column
title: 플레이리스트에 type 칼럼을 추가해 자동 생성 목록을 구분한다
zone: design
status: 제안
recorder: kmssss56
ai: Claude
recorded: 2026-10-16
discussed: 2026-10-16
refs: "#92"
files: domain/playlist/entity/Playlist.java, PlaylistType.java
summary: playlists.type(USER_CREATED / COMMUTE_GENERATED, 기본 USER_CREATED)을 추가하고 운영 DB에는 수동 ALTER 로 반영한다
---

## 왜
`POST /playlists/generate`가 만든 목록과 사용자가 직접 만든 목록을 화면과 API에서 구분해야 한다.

## 결정
- `playlists.type` 칼럼 추가: `USER_CREATED` | `COMMUTE_GENERATED`, `NOT NULL`, 기본값 `USER_CREATED`
- 기존 행(칼럼이 생기기 전)은 직접 만든 것으로 본다
- 목록·상세 응답(`PlaylistResponse`, `PlaylistDetailResponse`)에 `type`을 넣는다
- 생성 응답(`PlaylistGenerateResponse`)에도 같은 값이 `COMMUTE_GENERATED`로 나온다
- 다른 방식(별도 테이블, boolean 플래그)은 비교하지 않았다

## 운영 반영 (수동)
`ddl-auto: validate`라서 운영(EC2) DB에 직접 실행해야 한다.
`ALTER TABLE playlists ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'USER_CREATED';`

## 되돌릴 조건
유형이 두 가지보다 늘거나 유형마다 필드가 달라지면 별도 테이블로 나누는 것을 다시 검토한다.
