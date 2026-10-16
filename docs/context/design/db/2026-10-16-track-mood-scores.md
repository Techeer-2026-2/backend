---
id: 2026-10-16-track-mood-scores
title: 곡의 분위기를 8개 분위기 점수로 저장한다
zone: design
status: 제안
recorder: kmssss56
ai: Claude
recorded: 2026-10-16
discussed: 2026-10-16
refs: "#92"
files: domain/playlist/entity/TrackMood.java, TrackMoodScore.java (테이블 track_moods)
summary: 장르별 곡 저장 대신 곡마다 분위기 8개의 점수(0~1)를 별도 테이블에 저장한다
---

## 왜
시간대·날씨로 곡을 추천하려면 "비 오는 날 어울리는 곡"을 골라야 한다. 장르(Pop, Rock)는 분위기와 1:1이 아니다. 같은 K-Pop 안에도 잔잔한 곡과 신나는 곡이 섞여 있다.

## 결정
- 분위기 8개: GLOOMY, CALM, UPBEAT, ENERGETIC, FOCUS, ROMANTIC, NOSTALGIC, DREAMY
- 테이블 `track_moods(track_id, mood, score, source)`, `unique(track_id, mood)`, 점수 0~1
- 한 곡에 여러 분위기를 붙일 수 있다 (곡당 최대 3개, 점수 0.2 미만은 저장하지 않음)
- `source`: KEYWORD(검색어에서 정한 태그) / SCORED(여러 신호를 합친 점수)
- 기존 `tracks` 테이블은 바꾸지 않는다 (팀원 코드와 충돌 방지)

## 대안
| 대안 | 기각 이유 |
|---|---|
| `tracks.genre`로 필터 | 장르와 분위기가 맞지 않는다 |
| `tracks.mood` 칼럼 1개 | 한 곡의 복수 분위기를 못 담고, 점수(확신도)를 못 담는다 |
| 분위기를 매 요청마다 AI로 판정 | 느리고 비용이 들고 결과가 매번 달라진다 |

## 되돌릴 조건
추천 만족도가 낮아 분위기 체계를 바꿔야 하면, 8개를 늘리거나 줄이는 것은 enum과 점수 재계산으로 가능하다 (테이블 구조는 유지).

## 운영 반영 (수동)
`ddl-auto: validate`라서 EC2에는 테이블을 직접 만들어야 한다. 곡·점수 데이터 적재 방법은 `tools/track-moods/README.md`.
