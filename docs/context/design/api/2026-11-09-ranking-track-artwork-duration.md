---
id: 2026-11-09-ranking-track-artwork-duration
title: 차트 곡 정보에 앨범 커버·곡 길이를 ranking.track 컬럼으로 추가한다
zone: design
status: 제안
recorder: KimKyuBo0411
ai: Claude
recorded: 2026-11-09
discussed: 2026-11-09
refs: -
files: db/ranking/V4__track_artwork_duration.sql, domain/ranking/dto/RankingDtos.java, domain/ranking/repository/RankingRepository.java
summary: 차트 API 의 track 에 artworkUrl·trackTimeMillis 를 내려주기 위해 ranking.track 에 nullable 컬럼 2개를 추가하고 곡 등록 PUT 으로 받는다
---

## 왜
프론트 차트 화면(`/rankings/tracks/global`, `/nearby`)에 앨범 커버와 곡 길이가 필요하다. 차트는 메인 `tracks` 가 아니라 `ranking.track` 을 읽는데, 이 테이블에는 제목·아티스트·미리듣기·외부 링크만 있다.

## 결정
- `ranking.track` 에 `artwork_url varchar(2048)`, `track_time_millis integer (> 0)` 를 nullable 로 추가한다(V4). 기존 곡은 값이 없다
- 필드 이름은 `artworkUrl`, `trackTimeMillis`(밀리초). 플레이리스트 응답·iTunes 응답과 같은 이름이라 프론트가 곡 컴포넌트를 재사용할 수 있다
- 값은 `PUT /api/v1/music/tracks/{trackId}` 로 받는다. PUT 은 전체 교체라 생략하면 null 로 덮인다(기존 previewUrl 과 같은 동작)
- `artworkUrl` 은 다른 링크와 같이 HTTPS 만 허용한다
- `externalUrl` 은 외부 음악 서비스의 곡 페이지 링크로 그대로 둔다. 현재 채우는 곳은 없다

## 대안
| 대안 | 기각 이유 |
|---|---|
| 차트 조회 때 메인 `tracks` 를 JOIN | ID 형식이 다르고(`itunesTrackId` Long ↔ `"itunes-123"` 문자열) 스키마가 달라 랭킹이 플레이리스트 도메인에 묶인다 |
| `durationSec`(초) | 다른 API 가 모두 밀리초 `trackTimeMillis` 라 단위가 섞인다 |

## 한계
- 지금 onbeat 프론트는 곡 등록 PUT 을 호출하지 않는다. 누가 커버·길이를 채울지(프론트가 재생 전 등록 / 백엔드가 메인 tracks 에서 복사) 정해지기 전까지 실제 차트 값은 null 이다

## 되돌릴 조건
랭킹 곡 ID 가 메인 `tracks` 와 연결되면 메타데이터를 한 곳에서 읽도록 다시 본다.
