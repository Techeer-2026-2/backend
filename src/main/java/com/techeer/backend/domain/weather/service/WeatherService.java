package com.techeer.backend.domain.weather.service;

import com.techeer.backend.domain.weather.WeatherCondition;
import com.techeer.backend.domain.weather.dto.OpenWeatherApiResponse;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
public class WeatherService {

    private final RestClient openWeatherRestClient;
    private final String apiKey;

    public WeatherService(RestClient openWeatherRestClient, @Value("${external.openweather.api-key}") String apiKey) {
        this.openWeatherRestClient = openWeatherRestClient;
        this.apiKey = apiKey;
    }

    public WeatherCondition getCurrentCondition(double latitude, double longitude) {
        try {
            OpenWeatherApiResponse response = openWeatherRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/weather")
                            .queryParam("lat", "{lat}")
                            .queryParam("lon", "{lon}")
                            .queryParam("appid", apiKey)
                            .build(latitude, longitude))
                    .retrieve()
                    .body(OpenWeatherApiResponse.class);

            if (response == null || response.weather() == null || response.weather().isEmpty()
                    || response.weather().get(0) == null || response.weather().get(0).main() == null) {
                throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
            }

            return toWeatherCondition(response.weather().get(0).main());
        } catch (RestClientException e) {
            log.warn("OpenWeatherMap 호출 실패", e);
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, e);
        }
    }

    private WeatherCondition toWeatherCondition(String main) {
        return switch (main) {
            case "Clear" -> WeatherCondition.CLEAR;
            case "Rain", "Drizzle", "Thunderstorm" -> WeatherCondition.RAINY;
            case "Snow" -> WeatherCondition.SNOWY;
            // Clouds 외에 Mist/Fog/Haze 등 대기 현상도 전부 흐림으로 뭉뚱그린다.
            default -> WeatherCondition.CLOUDY;
        };
    }
}
