package com.techeer.backend.domain.weather.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class OpenWeatherClientConfig {

    @Value("${external.openweather.base-url}")
    private String baseUrl;

    @Bean
    public RestClient openWeatherRestClient(RestClient.Builder builder) {
        return builder.baseUrl(baseUrl).build();
    }
}
