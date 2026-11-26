package com.techeer.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.techeer.backend.TestcontainersConfiguration;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * ClickHouse 두 번째 DataSource 와 스키마 실행기를 실제 ClickHouse(Testcontainers)로 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ClickHouseSqlRunnerTest {

    private static final String TEST_LOCATION = "classpath*:clickhouse-runner-test/*.sql";

    @Autowired
    private ClickHouseSqlRunner runner;

    @Autowired
    @Qualifier(ClickHouseConfig.QUALIFIER)
    private NamedParameterJdbcTemplate clickHouseJdbc;

    @Autowired
    private DataSource primaryDataSource;

    @Test
    @DisplayName("이름 없이 주입하는 DataSource 는 그대로 PostgreSQL 이다")
    void primaryDataSourceStaysPostgres() throws SQLException {
        try (Connection connection = primaryDataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }
    }

    @Test
    @DisplayName("이름(clickHouse)으로 주입하면 ClickHouse 에 연결된다")
    void clickHouseDataSourceConnects() {
        Integer one = clickHouseJdbc.queryForObject("SELECT 1", Map.of(), Integer.class);

        assertThat(one).isEqualTo(1);
    }

    @Test
    @DisplayName("SQL 파일을 이름 순서대로 실행하고, 두 번 실행해도 결과가 같다")
    void runsScriptsInOrderAndIsIdempotent() {
        int firstRun = runner.runScripts(TEST_LOCATION);
        int secondRun = runner.runScripts(TEST_LOCATION);

        assertThat(firstRun).isEqualTo(2);
        assertThat(secondRun).isEqualTo(2);
        List<String> tables = clickHouseJdbc.queryForList(
                "SELECT name FROM system.tables WHERE database = currentDatabase() AND name LIKE 'runner_test_%' ORDER BY name",
                Map.of(), String.class);
        assertThat(tables).containsExactly("runner_test_events", "runner_test_second", "runner_test_view");
    }

    @Test
    @DisplayName("주석 줄과 빈 문장은 버리고 세미콜론으로 문장을 나눈다")
    void splitsStatements() {
        String sql = """
                -- 첫 줄 주석
                CREATE TABLE a (id Int64) ENGINE = Memory;

                   -- 들여쓴 주석
                CREATE TABLE b (id Int64) ENGINE = Memory;
                ;
                """;

        List<String> statements = ClickHouseSqlRunner.splitStatements(sql);

        assertThat(statements).containsExactly(
                "CREATE TABLE a (id Int64) ENGINE = Memory",
                "CREATE TABLE b (id Int64) ENGINE = Memory");
    }
}
