# design — DB·API 설계·트레이드오프

| id | 상태 | 한 줄 요약 |
|---|---|---|
| [2026-10-16-track-mood-scores](db/2026-10-16-track-mood-scores.md) | 제안 | 장르별 곡 저장 대신 곡마다 분위기 8개의 점수(0~1)를 별도 테이블에 저장한다 |
| [2026-10-16-playlist-type-column](db/2026-10-16-playlist-type-column.md) | 제안 | playlists.type 을 추가해 자동 생성 목록을 구분하고 운영 DB 는 수동 ALTER 로 반영한다 |
| [2026-10-16-no-location-ranking-in-generate](api/2026-10-16-no-location-ranking-in-generate.md) | 제안 | 주변 랭킹 가산점은 랭킹 곡과 iTunes 곡의 ID 연결이 생긴 뒤 별도 이슈로 한다 |
| [2026-10-16-playlist-share-port-from-pr83](api/2026-10-16-playlist-share-port-from-pr83.md) | 제안 | PR #83 의 자체 플레이리스트를 쓰지 않고 develop 의 Playlist·@MemberId 위에 공유 토큰과 청취 기록만 다시 구현한다 |
