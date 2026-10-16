package com.techeer.backend.domain.recommendation;

import com.techeer.backend.domain.playlist.entity.Track;
import com.techeer.backend.domain.playlist.entity.TrackMood;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * 후보 곡 중에서 상황에 맞는 곡을 골라 순서를 정한다. 데이터베이스나 외부 호출을 쓰지 않는 순수 계산이라 테스트하기 쉽다.
 *
 * <ol>
 *   <li>곡의 적합도 = Σ(상황 가중치[분위기] × 곡의 분위기 점수[분위기])
 *   <li>적합도가 높을수록 뽑힐 확률이 높은 <b>가중 무작위 추출</b>(같은 상황에서도 매번 다른 목록)
 *   <li>같은 가수는 {@link #MAX_PER_ARTIST}곡까지만, 같은 장르는 전체 곡 수의 {@link #MAX_GENRE_RATIO} 까지만(올림)
 * </ol>
 */
public final class PlaylistRecommender {

    static final double MIN_RELEVANCE = 0.05;
    static final int MAX_PER_ARTIST = 2;
    static final double MAX_GENRE_RATIO = 0.3;

    private PlaylistRecommender() {
    }

    public record Candidate(Track track, Map<TrackMood, Double> moodScores) {
    }

    public static double relevance(Candidate candidate, Map<TrackMood, Double> weights) {
        double sum = 0.0;
        for (Map.Entry<TrackMood, Double> e : weights.entrySet()) {
            sum += e.getValue() * candidate.moodScores().getOrDefault(e.getKey(), 0.0);
        }
        return sum;
    }

    public static List<Track> select(List<Candidate> candidates, Map<TrackMood, Double> weights, int size,
            RandomGenerator random) {
        // Efraimidis–Spirakis 가중 무작위 추출: 키 = u^(1/가중치). 키가 큰 순서대로 뽑으면 가중치가 클수록 앞에 올 확률이 높다.
        record Keyed(Track track, double key) {
        }
        List<Keyed> keyed = new ArrayList<>();
        for (Candidate c : candidates) {
            double r = relevance(c, weights);
            if (r < MIN_RELEVANCE) {
                continue;
            }
            double u = 1.0 - random.nextDouble(); // (0, 1]
            keyed.add(new Keyed(c.track(), Math.pow(u, 1.0 / (r * r))));
        }
        keyed.sort((a, b) -> Double.compare(b.key(), a.key()));

        int maxPerGenre = (int) Math.ceil(size * MAX_GENRE_RATIO);
        List<Track> picked = new ArrayList<>();
        Map<String, Integer> perArtist = new HashMap<>();
        Map<String, Integer> perGenre = new HashMap<>();
        for (Keyed k : keyed) {
            if (picked.size() >= size) {
                break;
            }
            String artist = k.track().getArtist() == null ? "" : k.track().getArtist().toLowerCase(Locale.ROOT);
            if (perArtist.getOrDefault(artist, 0) >= MAX_PER_ARTIST) {
                continue;
            }
            // 장르를 모르는 곡은 장르 제한을 적용하지 않는다.
            String genre = k.track().getGenre();
            if (genre != null && !genre.isBlank()
                    && perGenre.getOrDefault(genre.toLowerCase(Locale.ROOT), 0) >= maxPerGenre) {
                continue;
            }
            perArtist.merge(artist, 1, Integer::sum);
            if (genre != null && !genre.isBlank()) {
                perGenre.merge(genre.toLowerCase(Locale.ROOT), 1, Integer::sum);
            }
            picked.add(k.track());
        }
        return picked;
    }
}
