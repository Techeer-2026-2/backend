package com.techeer.backend.domain.tmap.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class TmapClientConfig {

    @Value("${external.tmap.base-url}")
    private String baseUrl;

    @Value("${external.tmap.app-key}")
    private String appKey;

    @Bean
    public RestClient tmapRestClient(RestClient.Builder builder) {
        return builder
                .baseUrl(baseUrl)
                .defaultHeader("appKey", appKey)
                .build();
    }
}
