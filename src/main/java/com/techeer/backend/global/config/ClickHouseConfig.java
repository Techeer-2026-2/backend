package com.techeer.backend.global.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 광고 집계용 ClickHouse 연결 (고도화 2026-11). PostgreSQL 과 별도인 두 번째 DataSource 다.
 *
 * <p>defaultCandidate = false 로 등록해서, 이름(@Qualifier("clickHouse"))을 붙여 요청한 곳에만 주입된다.
 * 그래서 JPA 와 기존 JdbcTemplate 은 지금처럼 스프링이 자동으로 만드는 PostgreSQL 연결을 그대로 쓴다.
 * (DataSource 를 그냥 빈으로 등록하면 스프링이 PostgreSQL 자동 연결을 만들지 않는다.)
 *
 * <p>연결은 처음 쿼리할 때 맺는다. 기동 시 연결 확인은 ClickHouseSqlRunner 가 스키마를 실행하면서 한다.
 */
@Configuration
@EnableConfigurationProperties(ClickHouseProperties.class)
public class ClickHouseConfig {

    public static final String QUALIFIER = "clickHouse";

    private static final int MAX_POOL_SIZE = 5;

    @Bean(defaultCandidate = false)
    @Qualifier(QUALIFIER)
    public DataSource clickHouseDataSource(ClickHouseProperties properties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("clickhouse");
        dataSource.setJdbcUrl(properties.url());
        dataSource.setUsername(properties.username());
        dataSource.setPassword(properties.password());
        dataSource.setMaximumPoolSize(MAX_POOL_SIZE);
        return dataSource;
    }

    @Bean(defaultCandidate = false)
    @Qualifier(QUALIFIER)
    public NamedParameterJdbcTemplate clickHouseJdbcTemplate(@Qualifier(QUALIFIER) DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
