package com.techeer.backend.domain.weather.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * OpenWeatherMap "현재 날씨" API의 원본 응답 모양. 서비스 내부에서만 쓰고,
 * {@link com.techeer.backend.domain.weather.WeatherCondition}으로 변환해서 내보낸다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenWeatherApiResponse(List<Weather> weather) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Weather(String main) {
    }
}
