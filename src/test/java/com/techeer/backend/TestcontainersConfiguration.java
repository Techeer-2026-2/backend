package com.techeer.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트용 PostgreSQL, ClickHouse 컨테이너.
 *
 * <p>@ServiceConnection 이 datasource URL/계정을 자동으로 주입하므로 테스트에서 DB 접속 정보를 따로 설정하지 않는다.
 * ClickHouse 는 @ServiceConnection 대상이 아니라서 clickhouse.* 설정값을 직접 넣는다.
 * 실행에는 Docker 가 떠 있어야 한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    @ConditionalOnProperty(name = "test.postgres.external", havingValue = "false", matchIfMissing = true)
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
    }

    /** 광고 집계용 ClickHouse. docker-compose.yml 과 같은 버전을 쓴다. */
    @Bean
    ClickHouseContainer clickHouseContainer() {
        return new ClickHouseContainer(DockerImageName.parse("clickhouse/clickhouse-server:26.8"));
    }

    @Bean
    DynamicPropertyRegistrar clickHouseProperties(ClickHouseContainer clickHouse) {
        return registry -> {
            registry.add("clickhouse.url", clickHouse::getJdbcUrl);
            registry.add("clickhouse.username", clickHouse::getUsername);
            registry.add("clickhouse.password", clickHouse::getPassword);
        };
    }
}
