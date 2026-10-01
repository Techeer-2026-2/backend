package com.techeer.backend.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 서버 기준 시각. 컨테이너 기본 시간대(UTC)와 상관없이 한국 시간으로 현재 시각을 구한다.
 *
 * <p>LocalDateTime.now() 대신 LocalDateTime.now(clock) 을 쓴다.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(SEOUL);
    }
}
