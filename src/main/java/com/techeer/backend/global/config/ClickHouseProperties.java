package com.techeer.backend.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.yml 의 clickhouse.* 설정값 (광고 집계, 고도화 2026-11).
 *
 * @param url        JDBC 주소. 예) jdbc:clickhouse://localhost:8123/default
 * @param username   사용자 이름
 * @param password   비밀번호 (로컬은 비어 있음)
 * @param initSchema true 이면 앱 기동 시 clickhouse/*.sql 을 실행한다. 기본값 true
 */
@ConfigurationProperties(prefix = "clickhouse")
public record ClickHouseProperties(
        String url,
        String username,
        @DefaultValue("") String password,
        @DefaultValue("true") boolean initSchema) {
}
