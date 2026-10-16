package com.techeer.backend.domain.weather.service;

import com.techeer.backend.domain.weather.WeatherCondition;
import com.techeer.backend.domain.weather.WeatherSnapshot;
import com.techeer.backend.domain.weather.dto.OpenWeatherApiResponse;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
public class WeatherService {

    private static final double KELVIN_OFFSET = 273.15;
    private static final Set<String> HAZY_MAINS = Set.of("Mist", "Smoke", "Haze", "Dust", "Fog", "Sand", "Ash");
    private static final Set<String> STORMY_MAINS = Set.of("Thunderstorm", "Squall", "Tornado");

    private final RestClient openWeatherRestClient;
    private final String apiKey;

    public WeatherService(RestClient openWeatherRestClient, @Value("${external.openweather.api-key}") String apiKey) {
        this.openWeatherRestClient = openWeatherRestClient;
        this.apiKey = apiKey;
    }

    public WeatherCondition getCurrentCondition(double latitude, double longitude) {
        return toWeatherCondition(fetchMainWeather(latitude, longitude).main());
    }

    /** 날씨 상태에 기온·안개·폭풍 여부까지 담아 돌려준다. */
    public WeatherSnapshot getCurrentSnapshot(double latitude, double longitude) {
        OpenWeatherApiResponse response = fetch(latitude, longitude);
        String main = response.weather().get(0).main();
        Double temperatureCelsius = response.main() == null || response.main().temp() == null
                ? null
                : response.main().temp() - KELVIN_OFFSET;
        return new WeatherSnapshot(toWeatherCondition(main), temperatureCelsius,
                HAZY_MAINS.contains(main), STORMY_MAINS.contains(main));
    }

    private OpenWeatherApiResponse.Weather fetchMainWeather(double latitude, double longitude) {
        return fetch(latitude, longitude).weather().get(0);
    }

    private OpenWeatherApiResponse fetch(double latitude, double longitude) {
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
            return response;
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
