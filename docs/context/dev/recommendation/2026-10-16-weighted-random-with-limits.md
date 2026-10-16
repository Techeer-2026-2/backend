---
id: 2026-10-16-weighted-random-with-limits
title: 추천은 규칙표 가중치 + 가중 무작위 추출이고 가수·장르 개수를 제한한다
zone: dev
status: 제안
recorder: kmssss56
ai: Claude
recorded: 2026-10-16
discussed: 2026-10-16
refs: "#92"
files: domain/recommendation/PlaylistRecommender.java, MoodRules.java, RecommendationService.java
summary: 날씨·기온·시간대·출퇴근을 분위기 가중치로 바꾸고, 적합도에 비례한 무작위로 뽑되 같은 가수 2곡·같은 장르 30%까지만
---

## 왜
상위 N곡만 고르면 같은 상황에서 항상 같은 목록이 나온다. 또 분위기 점수만 보면 특정 장르가 목록을 차지한다.

## 결정
- `MoodRules`가 날씨·기온(<5, ≥28)·안개·폭풍·시간대·출퇴근 가중치를 더해 정규화한다 (숫자는 초안)
- 곡 적합도 = Σ(분위기 가중치 × 곡의 분위기 점수), 0.05 미만이면 제외
- 뽑기: 키 = u^(1/적합도²) 순서대로 선택 (Efraimidis–Spirakis 가중 무작위). 같은 프로필로 4번 호출해 첫 목록과 겹친 곡이 0~1곡/15
- 같은 가수는 2곡까지
- **같은 장르는 전체 곡 수의 30%까지**(올림, 15곡이면 4~5곡). 장르가 비어 있는 곡은 제외 대상 아님
- 후보 장르가 적으면 요청한 곡 수보다 적게 돌려준다 (제한을 어기면서 채우지 않음)

## 근거 (실제 호출, 서울, 실제 날씨)
흐린 날 45곡 중 Instrumental+New Age가 21곡(47%) → 장르 제한 후 13곡(29%).

## 한계
- `FM STAR`, `Sleepy Nuts` 같은 수면·공부용 BGM 계정은 여전히 매번 1~3곡 섞인다. 장르 이름이 아니라 곡 단위 "BGM" 표시가 필요하다 (후속 과제).
- `Easy Listening`, `Classical Crossover`는 별개 장르로 세어져 제한을 피한다.

## 되돌릴 조건
곡 수가 자주 모자라면 비율(`MAX_GENRE_RATIO`)을 올리거나 비슷한 장르를 묶는다.
