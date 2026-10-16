#!/usr/bin/env python3
"""수집한 곡(track-seed-report.csv)에 분위기 점수를 계산해 track-moods.csv 를 만든다.

세 가지 신호를 합산한다 (서버가 아니라 개발자 컴퓨터에서 한 번 돌리는 도구다).
  1) keyword : 어떤 분위기 검색어로 찾았는지 (있으면 1, 없으면 0)
  2) dataset : Spotify 곡 수치 데이터셋(valence, energy …)에서 제목+가수로 찾은 곡의 수치 → 분위기
  3) llm     : 로컬 Ollama 모델이 제목·가수·장르를 보고 고른 분위기 1~2개 (첫 답 1.0, 둘째 0.5)
각 분위기 점수 = 사용할 수 있는 신호의 가중 평균. 한 신호가 틀려도 순위가 크게 흔들리지 않게 한다.

사용 예:
  python3 tools/track-moods/score_tracks.py \
      --report build/track-seed-report.csv --out build/track-moods.csv \
      --spotify /경로/dataset.csv --ollama-model llama3.2:3b
"""
import argparse
import collections
import csv
import json
import re
import sys
import time
import unicodedata
import urllib.request

MOODS = ["GLOOMY", "CALM", "UPBEAT", "ENERGETIC", "FOCUS", "ROMANTIC", "NOSTALGIC", "DREAMY"]
WEIGHTS = {"keyword": 0.4, "dataset": 0.4, "llm": 0.2}
DATASET_MOODS = {"GLOOMY", "CALM", "UPBEAT", "ENERGETIC", "FOCUS"}   # 수치로 판단 가능한 분위기
MIN_SCORE = 0.2          # 이보다 낮은 분위기는 저장하지 않는다
MAX_MOODS_PER_TRACK = 3

PROMPT = """You tag songs for a commute playlist app. Choose 1 or 2 moods that best describe the song from this list only:
GLOOMY (sad, melancholic), CALM (relaxed, soft), UPBEAT (happy, bright), ENERGETIC (intense, high tempo), FOCUS (background, study), ROMANTIC (love), NOSTALGIC (retro, memories), DREAMY (hazy, atmospheric).
If you do not know the song, judge from the title and genre. Answer JSON only: {"moods":["..."]}
Song: "%s" by %s (genre: %s)"""


def clamp(x):
    return max(0.0, min(1.0, x))


def norm(s):
    s = unicodedata.normalize("NFKC", s or "").lower()
    s = re.sub(r"\(.*?\)|\[.*?\]|- .*$", "", s)
    s = re.sub(r"feat\..*|ft\..*", "", s)
    return re.sub(r"[^0-9a-z가-힣]", "", s)


def artist_set(s):
    parts = re.split(r"[;,&]|feat\.| x | and ", unicodedata.normalize("NFKC", s or "").lower())
    return {norm(p) for p in parts if norm(p)}


def dataset_scores(row):
    """Spotify 수치(0~1) → 분위기 점수. 임계값은 --spotify 로 측정해 정한 초안이다."""
    v, e, inst = float(row["valence"]), float(row["energy"]), float(row["instrumentalness"])
    gloomy = clamp((0.55 - v) / 0.45) * (1.0 if e < 0.7 else clamp((0.9 - e) / 0.2))
    return {
        "GLOOMY": gloomy,
        "CALM": clamp((0.6 - e) / 0.4),
        "UPBEAT": clamp((v - 0.45) / 0.45) * clamp((e - 0.3) / 0.4),
        "ENERGETIC": clamp((e - 0.6) / 0.3),
        "FOCUS": clamp(inst / 0.8) * clamp((0.6 - e) / 0.4),
    }


def load_dataset(path):
    index = collections.defaultdict(list)
    with open(path, encoding="utf-8") as f:
        for r in csv.DictReader(f):
            index[norm(r["track_name"])].append(r)
    return index


def match_dataset(index, title, artist):
    best = None
    mine = artist_set(artist)
    for r in index.get(norm(title), []):
        theirs = artist_set(r["artists"])
        if mine & theirs or any(a in b or b in a for a in mine for b in theirs):
            if best is None or int(r["popularity"]) > int(best["popularity"]):
                best = r
    return best


def ask_llm(url, model, title, artist, genre):
    body = json.dumps({"model": model, "prompt": PROMPT % (title, artist, genre or ""), "stream": False,
                       "format": "json", "options": {"temperature": 0}}).encode()
    req = urllib.request.Request(url + "/api/generate", body, {"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        data = json.load(resp)
    moods = json.loads(data["response"]).get("moods", [])
    return [m for m in moods if m in MOODS][:2]


def combine(keyword_moods, ds, llm):
    out = {}
    for mood in MOODS:
        signals = [("keyword", 1.0 if mood in keyword_moods else 0.0)]
        if ds is not None and mood in DATASET_MOODS:
            signals.append(("dataset", ds[mood]))
        if llm is not None:
            signals.append(("llm", 1.0 if llm[:1] == [mood] else 0.5 if mood in llm else 0.0))
        total = sum(WEIGHTS[n] for n, _ in signals)
        out[mood] = sum(WEIGHTS[n] * v for n, v in signals) / total
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--report", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--spotify", help="Spotify 곡 수치 데이터셋 CSV (없으면 이 신호는 건너뜀)")
    ap.add_argument("--ollama-url", default="http://localhost:11434")
    ap.add_argument("--ollama-model", default="llama3.2:3b")
    ap.add_argument("--no-llm", action="store_true")
    ap.add_argument("--cache", default="build/llm-cache.json", help="모델 답 캐시. 중간에 멈춰도 이어서 돈다.")
    args = ap.parse_args()

    rows = list(csv.DictReader(open(args.report, encoding="utf-8")))
    index = load_dataset(args.spotify) if args.spotify else {}
    try:
        cache = json.load(open(args.cache, encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError):
        cache = {}

    matched = llm_used = 0
    dist = collections.Counter()
    fields = ["itunesTrackId", "title", "artist", "collectionName", "genre", "artworkUrl", "previewUrl",
              "trackTimeMillis", "moods"]
    started = time.time()
    with open(args.out, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        for n, r in enumerate(rows, 1):
            kw = set(filter(None, r["keywordMoods"].split("|")))
            ds_row = match_dataset(index, r["title"], r["artist"]) if index else None
            ds = dataset_scores(ds_row) if ds_row else None
            matched += ds is not None
            llm = None
            if not args.no_llm:
                key = r["itunesTrackId"]
                if key not in cache:
                    try:
                        cache[key] = ask_llm(args.ollama_url, args.ollama_model, r["title"], r["artist"], r["genre"])
                    except Exception as e:   # 모델이 꺼져 있거나 답이 이상하면 이 곡은 llm 신호 없이 계산
                        print(f"[llm 실패] {r['title']}: {e}", file=sys.stderr)
                        cache[key] = None
                llm = cache[key]
                llm_used += llm is not None
                if n % 50 == 0:
                    json.dump(cache, open(args.cache, "w", encoding="utf-8"))
                    print(f"  {n}/{len(rows)}  ({time.time() - started:.0f}s)", file=sys.stderr)
            scores = combine(kw, ds, llm)
            top = sorted(((m, s) for m, s in scores.items() if s >= MIN_SCORE), key=lambda x: -x[1])[:MAX_MOODS_PER_TRACK]
            if not top:
                continue
            for m, _ in top:
                dist[m] += 1
            w.writerow({**{k: r[k] for k in fields[:-1]}, "moods": "|".join(f"{m}:{s:.2f}" for m, s in top)})
    json.dump(cache, open(args.cache, "w", encoding="utf-8"))
    print(f"곡 {len(rows)}개 중 데이터셋 매칭 {matched}, 모델 답 사용 {llm_used}")
    print("분위기별 곡 수:", dict(dist))


if __name__ == "__main__":
    main()
