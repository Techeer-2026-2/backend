package com.techeer.backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * BaseEntity 의 created_at / updated_at 자동 기록.
 *
 * <p>BackendApplication 에 붙이면 @WebMvcTest 가 JPA 빈을 찾다가 실패하므로 별도 설정 클래스로 둔다.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
