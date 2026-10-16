package com.techeer.backend.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.commuteprofile.entity.CommuteType;
import com.techeer.backend.domain.music.seed.TrackMoodImporter;
import com.techeer.backend.domain.playlist.entity.Track;
import com.techeer.backend.domain.playlist.entity.TrackMood;
import com.techeer.backend.domain.playlist.entity.TrackMoodScore;
import com.techeer.backend.domain.playlist.repository.TrackMoodScoreRepository;
import com.techeer.backend.domain.playlist.repository.TrackRepository;
import com.techeer.backend.domain.recommendation.PlaylistRecommender.Candidate;
import com.techeer.backend.domain.weather.WeatherCondition;
import com.techeer.backend.domain.weather.WeatherSnapshot;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 로컬 시험용 샘플(240곡)만으로도 모든 날씨·시간대·통근 조합에서 쓸 만한 길이의 목록이 나오는지 확인한다.
 * 가수·장르 제한이 실제 데이터에서 지켜지는지도 함께 본다. 무작위라서 같은 조합을 여러 번 뽑아 본다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RecommendationWithSampleTest {

    private static final Path SAMPLE = Path.of("tools/track-moods/sample/track-moods-sample.csv");
    private static final int SIZE = 15;
    private static final int ROUNDS = 5;
    private static final int MAX_PER_GENRE = 5; // ceil(15 * 0.3)

    @Autowired
    TrackMoodImporter importer;

    @Autowired
    TrackRepository trackRepository;

    @Autowired
    TrackMoodScoreRepository moodScoreRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void loadSample() {
        moodScoreRepository.deleteAll();
        trackRepository.deleteAll();
        importer.importFile(SAMPLE);
    }

    @Test
    void 모든_날씨_시간대_통근_조합에서_열_곡_이상이_나오고_제한이_지켜진다() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> checkAllCombinations());
    }

    private void checkAllCombinations() {
        SecureRandom random = new SecureRandom();
        List<WeatherSnapshot> weathers = new ArrayList<>();
        weathers.add(null); // 날씨 조회에 실패한 경우
        for (WeatherCondition c : WeatherCondition.values()) {
            weathers.add(new WeatherSnapshot(c, 20.0, false, false));
        }
        weathers.add(new WeatherSnapshot(WeatherCondition.CLEAR, 33.0, false, false));
        weathers.add(new WeatherSnapshot(WeatherCondition.SNOWY, -3.0, false, false));
        weathers.add(new WeatherSnapshot(WeatherCondition.RAINY, 18.0, false, true));

        int combos = 0;
        for (WeatherSnapshot weather : weathers) {
            for (TimeBand band : TimeBand.values()) {
                for (CommuteType type : CommuteType.values()) {
                    Map<TrackMood, Double> weights = MoodRules.weights(new RecommendationContext(weather, band, type));
                    List<Candidate> pool = candidates(weights);
                    for (int round = 0; round < ROUNDS; round++) {
                        checkOne(weather + " / " + band + " / " + type, pool, weights, random);
                    }
                    combos++;
                }
            }
        }
        assertThat(combos).isEqualTo(weathers.size() * TimeBand.values().length * CommuteType.values().length);
    }

    private void checkOne(String label, List<Candidate> pool, Map<TrackMood, Double> weights, RandomGenerator random) {
        List<Track> picked = PlaylistRecommender.select(pool, weights, SIZE, random);

        assertThat(picked).as(label).hasSizeGreaterThanOrEqualTo(10);
        Map<String, Integer> perArtist = new HashMap<>();
        Map<String, Integer> perGenre = new HashMap<>();
        for (Track t : picked) {
            perArtist.merge(t.getArtist(), 1, Integer::sum);
            perGenre.merge(String.valueOf(t.getGenre()), 1, Integer::sum);
        }
        assertThat(perArtist.values()).as(label + " 가수별 곡 수").allMatch(n -> n <= 2);
        assertThat(perGenre.values()).as(label + " 장르별 곡 수").allMatch(n -> n <= MAX_PER_GENRE);
    }

    private List<Candidate> candidates(Map<TrackMood, Double> weights) {
        Map<Long, Map<TrackMood, Double>> scores = new LinkedHashMap<>();
        Map<Long, Track> tracks = new LinkedHashMap<>();
        for (TrackMoodScore s : moodScoreRepository.findCandidates(weights.keySet(), 0.2)) {
            tracks.putIfAbsent(s.getTrack().getId(), s.getTrack());
            scores.computeIfAbsent(s.getTrack().getId(), id -> new EnumMap<>(TrackMood.class))
                    .put(s.getMood(), s.getScore());
        }
        List<Candidate> result = new ArrayList<>();
        scores.forEach((id, m) -> result.add(new Candidate(tracks.get(id), m)));
        return result;
    }
}
