---
id: 2026-10-16-track-sample-in-repo
title: 로컬 시험용 곡 샘플 240곡을 공개 레포에 올린다
zone: etc
status: 제안
recorder: kmssss56
ai: Claude
recorded: 2026-10-16
discussed: 2026-10-16
refs: "#92"
files: tools/track-moods/sample/track-moods-sample.csv
summary: 팀원이 점수 계산 없이 추천을 시험하도록 분위기 점수가 붙은 곡 240곡(95KB)을 레포에 넣고, 나중에 지울 때 이 기록을 갱신한다
---

## 왜
추천(`POST /playlists/generate`)을 시험하려면 곡과 분위기 점수가 DB에 있어야 한다. 전체(3,067곡)를 만들려면 iTunes 수집 6분, 점수 계산 약 30분, 로컬 모델(Ollama)과 외부 수치 데이터셋이 필요해서 팀원이 따라 하기 어렵다.

## 결정
- 3,067곡 중 분위기마다 30곡씩 240곡을 골라 `tools/track-moods/sample/`에 올린다 (가수 중복 없음, 장르 41종, 한국 곡 51곡)
- 전체 3,067곡 파일과 외부 수치 데이터셋은 올리지 않는다
- `RecommendationWithSampleTest`가 이 샘플만으로 모든 날씨·시간대·통근 조합(120개)에서 15곡이 채워지는지 확인한다

## 알려진 위험 (확인하지 못함)
- 이 레포는 **공개(PUBLIC)** 이다. 파일에는 iTunes에서 받은 제목·가수·앨범 이미지 주소·미리듣기 주소가 있고, Apple 약관이 이를 저장·재배포하는 것을 허용하는지 **확인하지 못했다**
- 파일을 지워도 git 이력에는 남는다
- 분위기 점수에는 Spotify 수치 데이터셋(라이선스 표기 bsd)에서 계산된 값이 섞여 있다 (원본 데이터는 파일에 없음)
- 팀(Techeer 학습 프로젝트)이 사용자 요청으로 위험을 알고 올리기로 했다

## 삭제할 때 (되돌릴 조건)
약관 문제가 확인되거나 외부에 공개할 때 샘플을 지우고, 이 기록을 `대체됨`으로 바꾸는 새 기록에 지운 날짜·PR·이유를 적는다. 로컬 시험은 `tools/track-moods/README.md`의 ①~③으로 직접 만든 데이터로 대신한다.
