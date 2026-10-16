package com.techeer.backend.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.techeer.backend.domain.playlist.entity.Track;
import com.techeer.backend.domain.playlist.entity.TrackMood;
import com.techeer.backend.domain.recommendation.PlaylistRecommender.Candidate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PlaylistRecommenderTest {

    private static Track track(long id, String artist) {
        Track t = Track.builder().itunesTrackId(id).title("곡" + id).artist(artist).build();
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    private static Candidate candidate(long id, String artist, TrackMood mood, double score) {
        return new Candidate(track(id, artist), Map.of(mood, score));
    }

    private static Candidate genreCandidate(long id, String genre, double score) {
        Track t = Track.builder().itunesTrackId(id).title("곡" + id).artist("가수" + id).genre(genre).build();
        ReflectionTestUtils.setField(t, "id", id);
        return new Candidate(t, Map.of(TrackMood.GLOOMY, score));
    }

    private static final Map<TrackMood, Double> RAIN = Map.of(TrackMood.GLOOMY, 0.7, TrackMood.CALM, 0.3);

    private static List<Long> ids(List<Track> tracks) {
        return tracks.stream().map(Track::getId).toList();
    }

    @Test
    void 요청한_곡_수만큼만_고르고_중복은_없다() {
        List<Candidate> pool = new ArrayList<>();
        for (long i = 1; i <= 40; i++) {
            pool.add(candidate(i, "가수" + i, TrackMood.GLOOMY, 0.9));
        }

        List<Track> picked = PlaylistRecommender.select(pool, RAIN, 15, RandomGenerator.of("L64X128MixRandom"));

        assertThat(picked).hasSize(15);
        assertThat(new HashSet<>(ids(picked))).hasSize(15);
    }

    @Test
    void 상황과_무관한_곡은_뽑지_않는다() {
        List<Candidate> pool = List.of(
                candidate(1, "a", TrackMood.GLOOMY, 0.9),
                candidate(2, "b", TrackMood.ENERGETIC, 1.0),   // 비 오는 날 가중치가 없는 분위기
                candidate(3, "c", TrackMood.GLOOMY, 0.01));    // 점수가 너무 낮음

        List<Track> picked = PlaylistRecommender.select(pool, RAIN, 10, RandomGenerator.of("L64X128MixRandom"));

        assertThat(ids(picked)).containsExactly(1L);
    }

    @Test
    void 같은_가수는_두_곡까지만_고른다() {
        List<Candidate> pool = new ArrayList<>();
        for (long i = 1; i <= 6; i++) {
            pool.add(candidate(i, "같은가수", TrackMood.GLOOMY, 0.9));
        }
        pool.add(candidate(7, "다른가수", TrackMood.GLOOMY, 0.9));

        List<Track> picked = PlaylistRecommender.select(pool, RAIN, 10, RandomGenerator.of("L64X128MixRandom"));

        assertThat(picked).hasSize(3);
        assertThat(picked.stream().filter(t -> t.getArtist().equals("같은가수"))).hasSize(2);
    }

    @Test
    void 같은_장르는_전체_곡_수의_30퍼센트까지만_고른다() {
        List<Candidate> pool = new ArrayList<>();
        for (long i = 1; i <= 20; i++) {
            pool.add(genreCandidate(i, "Instrumental", 0.95));
        }
        for (long i = 21; i <= 40; i++) {
            pool.add(genreCandidate(i, i % 2 == 0 ? "K-Pop" : "Pop", 0.5));
        }

        List<Track> picked = PlaylistRecommender.select(pool, RAIN, 15, RandomGenerator.of("L64X128MixRandom"));

        assertThat(picked).hasSize(15);
        assertThat(picked.stream().filter(t -> t.getGenre().equals("Instrumental"))).hasSizeLessThanOrEqualTo(5);
        assertThat(picked.stream().filter(t -> t.getGenre().equals("K-Pop"))).hasSizeLessThanOrEqualTo(5);
        assertThat(picked.stream().filter(t -> t.getGenre().equals("Pop"))).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void 장르가_한_종류뿐이면_제한_때문에_곡_수가_줄어든다() {
        List<Candidate> pool = new ArrayList<>();
        for (long i = 1; i <= 20; i++) {
            pool.add(genreCandidate(i, "Instrumental", 0.9));
        }

        List<Track> picked = PlaylistRecommender.select(pool, RAIN, 10, RandomGenerator.of("L64X128MixRandom"));

        assertThat(picked).hasSize(3);
    }

    @Test
    void 적합도가_높은_곡이_더_자주_뽑히지만_낮은_곡도_가끔_뽑힌다() {
        List<Candidate> pool = List.of(
                candidate(1, "a", TrackMood.GLOOMY, 1.0),    // 적합도 0.70
                candidate(2, "b", TrackMood.GLOOMY, 0.3));   // 적합도 0.21
        int highFirst = 0;
        int lowFirst = 0;
        for (int seed = 0; seed < 400; seed++) {
            RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
            // 시드를 바꾸는 대신 서로 다른 인스턴스를 순서대로 쓰면 결과가 달라진다
            List<Track> picked = PlaylistRecommender.select(pool, RAIN, 1, random);
            if (picked.get(0).getId() == 1L) {
                highFirst++;
            } else {
                lowFirst++;
            }
        }

        assertThat(highFirst).isGreaterThan(lowFirst);
        assertThat(lowFirst).isGreaterThan(0);
    }

    @Test
    void 같은_상황에서도_매번_같은_목록이_나오지는_않는다() {
        List<Candidate> pool = new ArrayList<>();
        for (long i = 1; i <= 60; i++) {
            pool.add(candidate(i, "가수" + i, TrackMood.GLOOMY, 0.8));
        }
        Set<List<Long>> distinct = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            distinct.add(ids(PlaylistRecommender.select(pool, RAIN, 10, RandomGenerator.of("L64X128MixRandom"))));
        }

        assertThat(distinct.size()).isGreaterThan(1);
    }

    @Test
    void 후보가_없으면_빈_목록을_돌려준다() {
        assertThat(PlaylistRecommender.select(List.of(), RAIN, 10, RandomGenerator.of("L64X128MixRandom"))).isEmpty();
    }
}
