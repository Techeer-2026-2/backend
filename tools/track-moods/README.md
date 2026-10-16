# 곡 분위기 데이터 만들기 (개발자 컴퓨터에서 한 번 하는 작업)

날씨·시간대 추천(`POST /api/v1/playlists/generate`)이 쓰는 곡과 분위기 점수를 준비한다.
서버가 실행하는 기능이 아니라 **데이터 준비 도구**다. 운영 서버는 마지막 3단계(DB 반영)만 한다.

```
① 수집 (Java, iTunes 호출)  →  ② 점수 계산 (Python, 개발자 컴퓨터)  →  ③ DB 반영 (Java, 외부 호출 없음)
 build/track-seed-report.csv     build/track-moods.csv
```

## ① 수집 — 분위기별 검색어로 iTunes 에서 곡을 모은다
```bash
docker compose up -d db          # 로컬 DB
SPRING_PROFILES_ACTIVE=local,track-seed ./gradlew bootRun
```
- 검색어 목록은 `TrackSeedCatalog`. 영어·한국어 약 70개, 약 6분.
- iTunes 호출 제한을 지키려고 검색 사이 4초 쉬고, 실패하면 60초 쉬었다 최대 2번 다시 시도하며,
  3번 연속 실패하면 막힌 것으로 보고 중단한다. 이미 저장된 곡은 다시 만들지 않으므로 다시 돌려도 안전하다.
- 곡은 `tracks`, 분위기는 `track_moods`(source=KEYWORD)에 저장되고, 점수 계산용 `build/track-seed-report.csv` 가 만들어진다.
- 설정: `track-seed.delay-ms`(기본 4000), `track-seed.retry-wait-ms`(60000), `track-seed.max-retries`(2),
  `track-seed.max-consecutive-failures`(3), `track-seed.report-path`.

## ② 점수 계산 — 여러 신호를 합산해 `build/track-moods.csv` 를 만든다
```bash
python3 tools/track-moods/score_tracks.py \
    --report build/track-seed-report.csv --out build/track-moods.csv \
    --spotify /경로/dataset.csv --ollama-model llama3.2:3b
```
| 신호 | 가중치 | 설명 |
|---|---|---|
| keyword | 0.4 | 어떤 분위기 검색어로 찾았는지 |
| dataset | 0.4 | Spotify 곡 수치 데이터셋(제목+가수로 매칭된 곡만)의 valence·energy 등 → 분위기 |
| llm | 0.2 | 로컬 Ollama 모델이 제목·가수·장르를 보고 고른 분위기 |

- 쓸 수 없는 신호(데이터셋에 없는 곡 등)는 빼고 남은 신호끼리 가중 평균한다. 0.2 미만은 버리고 곡당 최대 3개 분위기만 남긴다.
- `--spotify`(없으면 건너뜀)는 Hugging Face `maharshipandya/spotify-tracks-dataset` 의 `dataset.csv`.
  **레포에 넣지 않는다.** 한국 곡은 거의 없어서(한글 제목 14곡) 매칭률이 낮다(우리 곡의 약 24%).
- `--no-llm` 으로 모델 없이 돌릴 수 있고, 모델 답은 `--cache`(기본 `build/llm-cache.json`)에 저장돼 중간에 멈춰도 이어서 돈다.
- 가중치·임계값은 검증된 값이 아니라 초안이다(스크립트 상단 상수).

## ③ DB 반영 — 외부 호출 없이 CSV 만 읽는다
```bash
SPRING_PROFILES_ACTIVE=local,track-import ./gradlew bootRun --args='--track-import.file=build/track-moods.csv'
```
- 곡이 없으면 만들고, 분위기는 파일 내용으로 **교체**한다(파일에 없는 기존 분위기는 삭제). 여러 번 돌려도 같은 결과.
- 형식이 틀린 줄은 건너뛰고 개수를 알려 주며, 헤더가 다르면 아무것도 저장하지 않고 실패한다.

## 로컬에서 바로 시험하기 — 샘플 240곡 (①②를 건너뛴다)
레포에 `tools/track-moods/sample/track-moods-sample.csv` 가 들어 있다. 점수 계산을 끝낸 곡 3,067곡 중 분위기마다 30곡씩 골랐고
(가수 중복 없음, 장르 41종, 한국 곡 51곡), ③만 하면 `POST /api/v1/playlists/generate` 를 바로 시험할 수 있다.
```bash
docker compose up -d db
SPRING_PROFILES_ACTIVE=local,track-import ./gradlew bootRun --args='--track-import.file=tools/track-moods/sample/track-moods-sample.csv'
```
- 날씨 조회에는 `OPENWEATHER_API_KEY` 가 필요하다 (`.env.example` 참고). 없으면 시간대·통근 구분만으로 추천한다.
- 이 샘플로 모든 날씨·시간대·통근 조합에서 15곡이 채워지는지 `RecommendationWithSampleTest` 가 확인한다.
- 곡 수가 적어서 추천 목록이 겹치기 쉽다. 추천 품질을 보려면 전체 3,067곡으로 ①~③을 직접 돌린다.
- 파일에는 iTunes 에서 받은 곡 정보(제목·가수·앨범 이미지·미리듣기 주소)가 들어 있다. 레포가 공개라서 올리기 전에 확인했고,
  나중에 지울 때의 기록은 `docs/context` 의 `2026-10-16-track-sample-in-repo` 에 있다.

## 알려진 한계
- 분위기 라벨의 정확도는 검증되지 않았다. 독립 기준(Spotify 수치)으로 재 보면 검색어 태그 62%, llama3.2 48% 수준이었다(123곡, 임계값은 직접 정한 값).
- 사용자가 나중에 플레이리스트에 담은 새 곡은 분위기 점수가 없어서 자동 생성에 쓰이지 않는다. 늘리려면 ①~③을 다시 돌린다.
