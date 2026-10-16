package com.techeer.backend.domain.recommendation.config;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RecommendationConfig {

    /** 여러 요청이 동시에 써도 안전한 난수 생성기. 테스트에서는 시드를 고정한 것으로 바꿔 끼운다. */
    @Bean
    public RandomGenerator recommendationRandom() {
        return new SecureRandom();
    }
}
