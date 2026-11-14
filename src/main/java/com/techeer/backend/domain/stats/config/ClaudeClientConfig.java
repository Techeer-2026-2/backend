package com.techeer.backend.domain.stats.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ClaudeClientConfig {

    @Value("${external.claude.base-url}")
    private String baseUrl;

    @Bean
    public RestClient claudeRestClient(RestClient.Builder builder) {
        return builder
                .baseUrl(baseUrl)
                .build();
    }
}
