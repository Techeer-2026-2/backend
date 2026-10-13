package com.techeer.backend.domain.kakao.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
public class KakaoClientConfig {

    @Value("${external.kakao.base-url}")
    private String baseUrl;

    @Value("${external.kakao.rest-api-key}")
    private String restApiKey;

    @Bean
    public RestClient kakaoRestClient(RestClient.Builder builder) {
        return builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
                .build();
    }
}
