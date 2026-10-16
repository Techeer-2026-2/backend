package com.techeer.backend.domain.music.seed;

import com.techeer.backend.domain.playlist.entity.TrackMood;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 분위기별로 iTunes 에서 어떤 검색어로 곡을 모을지 적어 둔 목록.
 *
 * <p>검색어만으로는 엉뚱한 곡이 섞인다(예: "발라드" → 클래식 Ballade). 그래서
 * ① 모든 검색에 {@link #EXCLUDED_GENRES} 를 적용하고 ② 필요한 검색어에는 허용 장르를 따로 건다.
 * 그래도 틀린 곡은 남으므로, 수집 후 CSV 리포트를 보고 사람이 다듬는 것을 전제로 한다.
 */
public final class TrackSeedCatalog {

    /** 통근길 추천에 어울리지 않는 장르. 모든 검색에서 뺀다. */
    public static final Set<String> EXCLUDED_GENRES = Set.of(
            "Classical", "Metal", "Comedy", "Children's Music", "Holiday", "Spoken Word", "Karaoke");

    public record Query(String term, Set<String> allowedGenres) {
        /** allowedGenres 가 비어 있으면 제외 장르만 아니면 모두 받는다. */
        public static Query of(String term, String... allowedGenres) {
            return new Query(term, Set.of(allowedGenres));
        }

        public boolean accepts(String genre) {
            if (genre != null && EXCLUDED_GENRES.contains(genre)) {
                return false;
            }
            return allowedGenres.isEmpty() || allowedGenres.contains(genre);
        }
    }

    /** 분위기별 검색어. 영어와 한국어를 섞어 한국 곡도 모은다. */
    public static final Map<TrackMood, List<Query>> QUERIES = Map.of(
            TrackMood.GLOOMY, List.of(
                    Query.of("sad song"), Query.of("heartbreak"), Query.of("rainy day"), Query.of("broken heart"),
                    Query.of("lonely night"), Query.of("crying"), Query.of("이별 노래"), Query.of("이별 발라드"),
                    Query.of("슬픈 노래"), Query.of("비 오는 날 노래"), Query.of("그리움"),
                    Query.of("발라드", "K-Pop", "R&B/Soul")),
            TrackMood.CALM, List.of(
                    Query.of("lo-fi"), Query.of("acoustic"), Query.of("chill"), Query.of("soft piano"),
                    Query.of("relaxing"), Query.of("coffee shop"), Query.of("잔잔한 노래"), Query.of("감성 어쿠스틱"),
                    Query.of("카페 음악"), Query.of("편안한 노래")),
            TrackMood.UPBEAT, List.of(
                    Query.of("happy"), Query.of("feel good"), Query.of("summer pop"), Query.of("good vibes"),
                    Query.of("sunshine"), Query.of("road trip"), Query.of("기분 좋아지는 노래"), Query.of("여름 노래"),
                    Query.of("행복한 노래"), Query.of("신나는 노래", "K-Pop", "Pop", "Dance")),
            TrackMood.ENERGETIC, List.of(
                    Query.of("workout"), Query.of("dance"), Query.of("hip hop"), Query.of("gym motivation"),
                    Query.of("running"), Query.of("edm"), Query.of("파워풀한 노래"), Query.of("운동할 때 듣는 노래"),
                    Query.of("댄스곡"), Query.of("k-pop hits", "K-Pop")),
            TrackMood.FOCUS, List.of(
                    Query.of("study music"), Query.of("focus"), Query.of("deep work"), Query.of("instrumental"),
                    Query.of("ambient study"), Query.of("집중 음악"), Query.of("공부할 때 듣는 음악")),
            TrackMood.ROMANTIC, List.of(
                    Query.of("love song"), Query.of("romantic"), Query.of("first date"), Query.of("wedding song"),
                    Query.of("사랑 노래"), Query.of("설렘"), Query.of("연애 노래")),
            TrackMood.NOSTALGIC, List.of(
                    Query.of("90s hits"), Query.of("80s hits"), Query.of("2000s hits"), Query.of("throwback"),
                    Query.of("retro"), Query.of("추억의 노래"), Query.of("90년대 가요"), Query.of("옛날 노래")),
            TrackMood.DREAMY, List.of(
                    Query.of("dream pop"), Query.of("ambient"), Query.of("night drive"), Query.of("synthwave"),
                    Query.of("새벽 감성"), Query.of("몽환적인"), Query.of("밤 드라이브")));

    private TrackSeedCatalog() {
    }
}
