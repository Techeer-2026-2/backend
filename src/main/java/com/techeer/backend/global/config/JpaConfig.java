package com.techeer.backend.global.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * BaseEntity 의 created_at / updated_at 자동 기록. 시각은 ClockConfig 의 한국 시간 기준.
 *
 * <p>BackendApplication 에 붙이면 @WebMvcTest 가 JPA 빈을 찾다가 실패하므로 별도 설정 클래스로 둔다.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}
