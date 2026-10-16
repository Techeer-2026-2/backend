---
id: 2026-10-16-no-location-ranking-in-generate
title: 주변 랭킹(위치 기반) 가산점은 플레이리스트 자동 생성에 넣지 않는다
zone: design
status: 대체됨
recorder: kmssss56
ai: Claude
recorded: 2026-10-16
discussed: 2026-10-16
refs: "#92"
files: domain/recommendation/RecommendationService.java
summary: 위치는 날씨 조회에만 쓰고, 주변 인기곡 가산점은 랭킹 곡과 iTunes 곡의 ID 연결이 생긴 뒤 별도 이슈로 한다
---

## 왜
요구사항은 "시간대·날씨 + 위치(주변 랭킹)" 추천이다. 그런데 랭킹(`GET /api/v1/rankings/tracks/nearby`)의 `ranking.track`은 문자열 ID만 있고 장르·분위기가 없다. 우리 `tracks`(iTunes ID)와 이어 주는 코드가 없다 (곡 저장소 통합 #65가 정해지지 않음).

## 결정
- `POST /api/v1/playlists/generate`는 출발지 좌표로 날씨를 조회하는 데만 위치를 쓴다
- 주변 랭킹 가산점은 이번 PR에서 제외한다

## 대안
| 대안 | 기각 이유 |
|---|---|
| iTunes ID를 랭킹 곡 ID로 쓰기로 팀과 약속한 뒤 이번 PR에 구현 | 팀 합의가 먼저 필요하고 이번 이슈의 범위를 넘는다 |

사용자(kmssss56)가 별도 위치 기반 이슈로 진행하기로 했다.

## 되돌릴 조건
랭킹과 곡 저장소의 ID 연결이 정해지면 `RecommendationService`에서 후보 적합도에 인기 가산점을 더하는 이슈를 만든다.

대체: `2026-10-16-location-popularity-boost` (#104). ID 연결 합의를 기다리지 않고 두 형식을 모두 받는 방식으로 구현했다.
