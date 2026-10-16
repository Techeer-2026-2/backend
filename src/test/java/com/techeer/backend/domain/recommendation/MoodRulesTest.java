package com.techeer.backend.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.techeer.backend.domain.commuteprofile.entity.CommuteType;
import com.techeer.backend.domain.playlist.entity.TrackMood;
import com.techeer.backend.domain.weather.WeatherCondition;
import com.techeer.backend.domain.weather.WeatherSnapshot;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MoodRulesTest {

    private static Map<TrackMood, Double> weights(WeatherSnapshot w, TimeBand t, CommuteType c) {
        return MoodRules.weights(new RecommendationContext(w, t, c));
    }

    private static TrackMood top(Map<TrackMood, Double> w) {
        return w.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
    }

    private static WeatherSnapshot weather(WeatherCondition c, Double temp) {
        return new WeatherSnapshot(c, temp, false, false);
    }

    @Test
    void 가중치의_합은_항상_1이다() {
        for (WeatherCondition c : WeatherCondition.values()) {
            for (TimeBand t : TimeBand.values()) {
                for (CommuteType type : CommuteType.values()) {
                    double sum = weights(weather(c, 15.0), t, type).values().stream().mapToDouble(d -> d).sum();
                    assertThat(sum).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
                }
            }
        }
    }

    @Test
    void 비_오는_퇴근길에는_우울하거나_잔잔한_분위기가_앞선다() {
        Map<TrackMood, Double> w = weights(weather(WeatherCondition.RAINY, 15.0), TimeBand.EVENING_RUSH, CommuteType.FROM_WORK);

        assertThat(top(w)).isIn(TrackMood.GLOOMY, TrackMood.CALM);
        assertThat(w.get(TrackMood.GLOOMY)).isGreaterThan(w.getOrDefault(TrackMood.ENERGETIC, 0.0));
    }

    @Test
    void 맑은_출근길에는_밝고_에너지_있는_분위기가_앞선다() {
        Map<TrackMood, Double> w = weights(weather(WeatherCondition.CLEAR, 20.0), TimeBand.MORNING_RUSH, CommuteType.TO_WORK);

        assertThat(top(w)).isIn(TrackMood.UPBEAT, TrackMood.ENERGETIC);
        assertThat(w.get(TrackMood.UPBEAT)).isGreaterThan(w.getOrDefault(TrackMood.GLOOMY, 0.0));
    }

    @Test
    void 눈_오는_밤에는_로맨틱하거나_몽환적인_분위기가_앞선다() {
        Map<TrackMood, Double> w = weights(weather(WeatherCondition.SNOWY, -2.0), TimeBand.NIGHT, CommuteType.OTHER);

        assertThat(top(w)).isIn(TrackMood.ROMANTIC, TrackMood.DREAMY, TrackMood.CALM);
    }

    @Test
    void 추우면_추억_분위기가_더해지고_더우면_밝은_분위기가_더해진다() {
        double coldNostalgic = weights(weather(WeatherCondition.CLOUDY, -3.0), TimeBand.DAYTIME, CommuteType.OTHER)
                .get(TrackMood.NOSTALGIC);
        double mildNostalgic = weights(weather(WeatherCondition.CLOUDY, 15.0), TimeBand.DAYTIME, CommuteType.OTHER)
                .get(TrackMood.NOSTALGIC);
        double hotUpbeat = weights(weather(WeatherCondition.CLEAR, 33.0), TimeBand.DAYTIME, CommuteType.OTHER)
                .get(TrackMood.UPBEAT);
        double mildUpbeat = weights(weather(WeatherCondition.CLEAR, 15.0), TimeBand.DAYTIME, CommuteType.OTHER)
                .get(TrackMood.UPBEAT);

        assertThat(coldNostalgic).isGreaterThan(mildNostalgic);
        assertThat(hotUpbeat).isGreaterThan(mildUpbeat);
    }

    @Test
    void 날씨가_없어도_시간대와_통근_구분만으로_가중치를_만든다() {
        Map<TrackMood, Double> w = weights(null, TimeBand.DAWN, CommuteType.TO_WORK);

        assertThat(w).isNotEmpty();
        assertThat(w.get(TrackMood.DREAMY)).isGreaterThan(0.0);
    }

    @Test
    void 안개_낀_날에는_집중_분위기가_생긴다() {
        Map<TrackMood, Double> w = weights(new WeatherSnapshot(WeatherCondition.CLOUDY, 15.0, true, false),
                TimeBand.DAYTIME, CommuteType.OTHER);

        assertThat(w.get(TrackMood.FOCUS)).isGreaterThan(0.0);
    }
}
