package com.techeer.backend.domain.recommendation;

import com.techeer.backend.domain.commuteprofile.entity.CommuteType;
import com.techeer.backend.domain.playlist.entity.TrackMood;
import com.techeer.backend.domain.weather.WeatherCondition;
import com.techeer.backend.domain.weather.WeatherSnapshot;
import java.util.EnumMap;
import java.util.Map;

/**
 * 상황(날씨·기온·시간대·통근 구분) → 어떤 분위기를 얼마나 원하는지(가중치).
 *
 * <p>규칙은 이 한 곳에만 둔다. 상황마다 해당하는 규칙의 가중치를 모두 더하고, 합이 1 이 되게 맞춘다.
 * 숫자는 검증된 값이 아니라 초안이다 — 추천이 어색하면 여기 숫자만 고치면 된다.
 */
public final class MoodRules {

    private static final double COLD_BELOW_CELSIUS = 5.0;
    private static final double HOT_FROM_CELSIUS = 28.0;

    private MoodRules() {
    }

    public static Map<TrackMood, Double> weights(RecommendationContext context) {
        Map<TrackMood, Double> w = new EnumMap<>(TrackMood.class);
        addWeather(w, context.weather());
        addTime(w, context.timeBand());
        addCommute(w, context.commuteType());
        double total = w.values().stream().mapToDouble(Double::doubleValue).sum();
        w.replaceAll((mood, weight) -> weight / total);
        return w;
    }

    private static void addWeather(Map<TrackMood, Double> w, WeatherSnapshot weather) {
        if (weather == null) {
            return;
        }
        WeatherCondition condition = weather.condition();
        if (condition == WeatherCondition.CLEAR) {
            add(w, TrackMood.UPBEAT, 0.6);
            add(w, TrackMood.ENERGETIC, 0.4);
            add(w, TrackMood.ROMANTIC, 0.2);
        } else if (condition == WeatherCondition.CLOUDY) {
            add(w, TrackMood.CALM, 0.6);
            add(w, TrackMood.DREAMY, 0.3);
            add(w, TrackMood.NOSTALGIC, 0.2);
        } else if (condition == WeatherCondition.RAINY) {
            add(w, TrackMood.GLOOMY, 0.6);
            add(w, TrackMood.CALM, 0.5);
            add(w, TrackMood.ROMANTIC, 0.2);
            add(w, TrackMood.NOSTALGIC, 0.2);
        } else if (condition == WeatherCondition.SNOWY) {
            add(w, TrackMood.ROMANTIC, 0.5);
            add(w, TrackMood.DREAMY, 0.4);
            add(w, TrackMood.CALM, 0.3);
        }
        if (weather.hazy()) {
            add(w, TrackMood.CALM, 0.4);
            add(w, TrackMood.FOCUS, 0.3);
            add(w, TrackMood.DREAMY, 0.3);
        }
        if (weather.stormy()) {
            add(w, TrackMood.GLOOMY, 0.3);
            add(w, TrackMood.ENERGETIC, 0.2);
        }
        Double temperature = weather.temperatureCelsius();
        if (temperature != null && temperature < COLD_BELOW_CELSIUS) {
            add(w, TrackMood.NOSTALGIC, 0.3);
            add(w, TrackMood.CALM, 0.2);
        } else if (temperature != null && temperature >= HOT_FROM_CELSIUS) {
            add(w, TrackMood.UPBEAT, 0.3);
            add(w, TrackMood.ENERGETIC, 0.2);
        }
    }

    private static void addTime(Map<TrackMood, Double> w, TimeBand band) {
        switch (band) {
            case DAWN -> {
                add(w, TrackMood.DREAMY, 0.5);
                add(w, TrackMood.CALM, 0.3);
            }
            case MORNING_RUSH -> {
                add(w, TrackMood.ENERGETIC, 0.4);
                add(w, TrackMood.UPBEAT, 0.3);
                add(w, TrackMood.FOCUS, 0.2);
            }
            case DAYTIME -> {
                add(w, TrackMood.UPBEAT, 0.2);
                add(w, TrackMood.FOCUS, 0.2);
            }
            case EVENING_RUSH -> {
                add(w, TrackMood.CALM, 0.3);
                add(w, TrackMood.NOSTALGIC, 0.2);
                add(w, TrackMood.ROMANTIC, 0.1);
                add(w, TrackMood.GLOOMY, 0.1);
            }
            case NIGHT -> {
                add(w, TrackMood.DREAMY, 0.4);
                add(w, TrackMood.CALM, 0.4);
                add(w, TrackMood.ROMANTIC, 0.2);
            }
        }
    }

    private static void addCommute(Map<TrackMood, Double> w, CommuteType type) {
        if (type == CommuteType.TO_WORK) {
            add(w, TrackMood.ENERGETIC, 0.2);
            add(w, TrackMood.FOCUS, 0.2);
        } else if (type == CommuteType.FROM_WORK) {
            add(w, TrackMood.CALM, 0.2);
            add(w, TrackMood.NOSTALGIC, 0.1);
        }
    }

    private static void add(Map<TrackMood, Double> w, TrackMood mood, double weight) {
        w.merge(mood, weight, Double::sum);
    }
}
