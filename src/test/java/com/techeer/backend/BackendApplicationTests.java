package com.techeer.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 스프링 컨텍스트가 실제 PostgreSQL 에 붙어서 정상적으로 기동되는지 확인한다.
 *
 * <p>빈 설정 오류·JPA 매핑 오류를 PR 단계에서 잡아내는 안전망 역할.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class BackendApplicationTests {

    @Test
    @DisplayName("애플리케이션 컨텍스트가 DB 연결과 함께 로드된다")
    void contextLoads() {
        // 컨텍스트 로딩 자체가 검증 대상이므로 본문은 비어 있다.
    }
}
