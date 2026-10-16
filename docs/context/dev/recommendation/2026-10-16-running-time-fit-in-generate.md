---
id: 2026-10-16-running-time-fit-in-generate
title: 곡 수를 정하지 않은 자동 생성은 통근 소요시간에 맞춰 곡을 고른다
zone: dev
status: 제안
recorder: KimKyuBo0411
ai: Claude
recorded: 2026-10-16
discussed: 2026-10-16
refs: "#90"
files: domain/playlist/service/RunningTimeAdjuster.java, domain/recommendation/RecommendationService.java
summary: size 가 없고 경로 소요시간을 알면 어림 곡 수+5곡을 가중 무작위로 뽑은 뒤 그 순서대로 소요시간을 넘지 않게(또는 덜 넘치게) 담는다
---

## 왜
`POST /playlists/generate` 는 곡 수(기본 15곡)만 보고 만들어서, 20분 통근이든 70분 통근이든 같은 길이가 나왔다.

## 결정
- `size` 를 보내면 그대로 쓴다. 안 보내고 `CommuteRoute.averageDurationMinutes` 가 0보다 크면 소요시간에 맞춘다. 둘 다 아니면 15곡
- 어림 곡 수 = ⌈소요시간 ÷ 후보 곡 평균 재생시간⌉. `PlaylistRecommender.select` 로 어림 곡 수 + 5곡을 뽑는다
- `RunningTimeAdjuster.fit` 이 뽑힌 순서대로 소요시간을 넘지 않게 담고, 남은 공백보다 덜 넘치는 곡이 있으면 하나 더 붙인다
- 재생시간을 아는 후보가 없거나 결과가 비면 15곡으로 만든다
- 응답(`PlaylistResponse`, `PlaylistDetailResponse`, `PlaylistGenerateResponse`)에 `totalDurationSec` 를 넣는다. 재생시간을 모르는 곡은 0초로 친다

## 대안
| 대안 | 기각 이유 |
|---|---|
| 최대 100곡을 뽑은 뒤 시간 맞춤 | 장르 30% 제한이 100곡 기준(30곡)으로 걸려 실제로 담기는 10곡 남짓에는 제한이 거의 안 걸린다 |

## 되돌릴 조건
긴 곡이 많아 +5곡 여유로도 소요시간을 자주 못 채우면 여유를 늘리거나, 모자랄 때 한 번 더 뽑는다.
