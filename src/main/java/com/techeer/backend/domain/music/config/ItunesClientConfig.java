package com.techeer.backend.domain.music.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ItunesClientConfig {

    @Value("${external.itunes.base-url}")
    private String baseUrl;

    @Bean
    public RestClient itunesRestClient(RestClient.Builder builder) {
        return builder.baseUrl(baseUrl).build();
    }
}
