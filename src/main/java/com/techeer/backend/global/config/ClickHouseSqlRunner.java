package com.techeer.backend.global.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 앱 기동 시 ClickHouse 스키마 SQL(classpath:clickhouse/*.sql)을 파일 이름 순서대로 실행한다.
 *
 * <p>SQL 파일은 모든 문장을 IF NOT EXISTS 로 써서 여러 번 실행해도 결과가 같아야 한다.
 * ClickHouse 에 연결할 수 없으면 예외가 나서 앱이 뜨지 않는다 (기동 시 연결 확인).
 * clickhouse.init-schema=false 이면 실행하지 않는다.
 *
 * <p>문장은 세미콜론으로 나누고, "--" 로 시작하는 줄은 주석으로 보고 버린다.
 * 그래서 SQL 문자열이나 주석 안에는 세미콜론을 쓰지 않는다.
 */
@Slf4j
@Component
public class ClickHouseSqlRunner implements ApplicationRunner {

    static final String SCHEMA_LOCATION = "classpath*:clickhouse/*.sql";

    private final NamedParameterJdbcTemplate clickHouseJdbc;
    private final ResourcePatternResolver resourceResolver;
    private final ClickHouseProperties properties;

    public ClickHouseSqlRunner(
            @Qualifier(ClickHouseConfig.QUALIFIER) NamedParameterJdbcTemplate clickHouseJdbc,
            ResourcePatternResolver resourceResolver,
            ClickHouseProperties properties) {
        this.clickHouseJdbc = clickHouseJdbc;
        this.resourceResolver = resourceResolver;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.initSchema()) {
            log.info("clickhouse.init-schema=false 라서 ClickHouse 스키마를 실행하지 않는다");
            return;
        }
        runScripts(SCHEMA_LOCATION);
    }

    /**
     * 위치 패턴에 맞는 SQL 파일을 파일 이름 순서대로 실행하고, 실행한 파일 수를 돌려준다.
     */
    public int runScripts(String locationPattern) {
        List<Resource> scripts = findScripts(locationPattern);
        for (Resource script : scripts) {
            List<String> statements = splitStatements(read(script));
            statements.forEach(statement -> clickHouseJdbc.getJdbcTemplate().execute(statement));
            log.info("ClickHouse 스키마 실행: {} (문장 {}개)", script.getFilename(), statements.size());
        }
        return scripts.size();
    }

    /**
     * SQL 파일 내용을 문장 목록으로 나눈다. "--" 주석 줄과 빈 문장은 버린다.
     */
    static List<String> splitStatements(String sql) {
        StringBuilder withoutComments = new StringBuilder();
        for (String line : sql.split("\\R")) {
            if (!line.strip().startsWith("--")) {
                withoutComments.append(line).append('\n');
            }
        }
        List<String> statements = new ArrayList<>();
        for (String statement : withoutComments.toString().split(";")) {
            if (!statement.isBlank()) {
                statements.add(statement.strip());
            }
        }
        return statements;
    }

    private List<Resource> findScripts(String locationPattern) {
        try {
            return Arrays.stream(resourceResolver.getResources(locationPattern))
                    .filter(Resource::exists)
                    .sorted(Comparator.comparing(Resource::getFilename))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("ClickHouse 스키마 파일을 찾지 못했다: " + locationPattern, e);
        }
    }

    private static String read(Resource script) {
        try {
            return script.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("ClickHouse 스키마 파일을 읽지 못했다: " + script.getFilename(), e);
        }
    }
}
