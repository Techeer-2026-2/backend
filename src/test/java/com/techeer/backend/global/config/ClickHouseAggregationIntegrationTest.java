package com.techeer.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.techeer.backend.TestcontainersConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * ClickHouse 집계 표(001_schema.sql)와 재계산, 조정 SQL(clickhouse/recompute)을 실제 ClickHouse(Testcontainers)로 검증한다.
 *
 * <p>S3 대신 같은 컬럼을 가진 시험 원본 표를 ${raw_source} 자리에 넣는다.
 * Kafka 에서 MV 로 들어오는 경로는 Kafka 설정(#168) 이 머지된 뒤에 따로 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ClickHouseAggregationIntegrationTest {

    private static final long CAMPAIGN_ID = 777L;
    private static final String DAY = "2026-11-21";
    private static final String PARTITION = "20261121";
    private static final String RAW_TABLE = "raw_clicks_test";

    @Autowired
    @Qualifier(ClickHouseConfig.QUALIFIER)
    private NamedParameterJdbcTemplate clickHouseJdbc;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = clickHouseJdbc.getJdbcTemplate();
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + RAW_TABLE + " (notification_id String, campaign_id Int64,"
                + " age_group String, gender String, event_at DateTime) ENGINE = MergeTree ORDER BY notification_id");
        clearAll();
    }

    @AfterEach
    void tearDown() {
        clearAll();
        jdbc.execute("DROP TABLE IF EXISTS " + RAW_TABLE);
    }

    @Test
    @DisplayName("분 표에 들어온 클릭은 롤업 MV 로 시간 표, 일 표에도 같은 합계로 쌓인다")
    void rollupKeepsMinuteHourDaySumsEqual() {
        insertClickFacts("2026-11-21 10:01:00", "20s", 2);
        insertClickFacts("2026-11-21 10:59:00", "20s", 3);
        insertClickFacts("2026-11-21 23:30:00", "30s", 4);
        insertClickFacts("2026-11-22 00:10:00", "30s", 1);

        assertThat(sum("click_facts", "toDate(minute)")).isEqualTo(9);
        assertThat(sum("click_facts_hour", "toDate(hour)")).isEqualTo(9);
        assertThat(sum("click_facts_day", "day")).isEqualTo(9);
        assertThat(jdbc.queryForObject(
                "SELECT sum(cnt) FROM click_facts_hour WHERE hour = toDateTime('2026-11-21 10:00:00')", Long.class))
                .isEqualTo(5);
    }

    @Test
    @DisplayName("재계산하면 원본의 같은 notification_id 3개가 1개로 세어지고, 분, 시간, 일 합계가 같다")
    void recomputeRemovesDuplicates() throws IOException {
        // 실시간 값에는 중복이 섞여 있다 (n1 이 3번 들어옴)
        insertClickFacts("2026-11-21 10:01:00", "20s", 3);
        insertClickFacts("2026-11-21 23:59:00", "30s", 1);
        insertRaw("n1", "2026-11-21 10:01:10", "20s");
        insertRaw("n1", "2026-11-21 10:01:10", "20s");
        insertRaw("n1", "2026-11-21 10:01:10", "20s");
        insertRaw("n2", "2026-11-21 23:59:30", "30s");

        runScript("clickhouse/recompute/recompute_clicks.sql");

        assertThat(sum("click_facts", "toDate(minute)")).isEqualTo(2);
        assertThat(sum("click_facts_hour", "toDate(hour)")).isEqualTo(2);
        assertThat(sum("click_facts_day", "day")).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 날짜를 두 번 재계산해도 결과가 같고, 다른 날짜는 건드리지 않는다")
    void recomputeIsIdempotentAndKeepsOtherDays() throws IOException {
        insertClickFacts("2026-11-21 10:01:00", "20s", 5);
        insertClickFacts("2026-11-22 00:10:00", "20s", 7);
        insertRaw("n1", "2026-11-21 10:01:10", "20s");
        insertRaw("n2", "2026-11-21 11:00:00", "20s");
        insertRaw("n3", "2026-11-22 00:10:00", "20s");  // 다른 날짜 원본은 이 날짜 재계산에 들어가지 않는다

        runScript("clickhouse/recompute/recompute_clicks.sql");
        long first = sum("click_facts", "toDate(minute)");
        runScript("clickhouse/recompute/recompute_clicks.sql");

        assertThat(first).isEqualTo(2);
        assertThat(sum("click_facts", "toDate(minute)")).isEqualTo(2);
        assertThat(sum("click_facts_day", "day")).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT sum(cnt) FROM click_facts WHERE toDate(minute) = '2026-11-22'", Long.class)).isEqualTo(7);
    }

    @Test
    @DisplayName("재계산 뒤 조정 SQL 의 원본 고유 건수와 ClickHouse 합계가 같다")
    void reconcileMatchesAfterRecompute() throws IOException {
        insertClickFacts("2026-11-21 10:01:00", "20s", 4);
        insertRaw("n1", "2026-11-21 10:01:10", "20s");
        insertRaw("n1", "2026-11-21 10:01:10", "20s");
        insertRaw("n2", "2026-11-21 10:02:00", "20s");

        Map<String, Object> before = jdbc.queryForMap(script("clickhouse/recompute/reconcile_clicks.sql").strip());
        runScript("clickhouse/recompute/recompute_clicks.sql");
        Map<String, Object> after = jdbc.queryForMap(script("clickhouse/recompute/reconcile_clicks.sql").strip());

        assertThat(((Number) before.get("s3_unique_count")).longValue()).isEqualTo(2);
        assertThat(((Number) before.get("ch_daily_count")).longValue()).isEqualTo(4);
        assertThat(((Number) after.get("s3_unique_count")).longValue()).isEqualTo(2);
        assertThat(((Number) after.get("ch_daily_count")).longValue()).isEqualTo(2);
    }

    /** 재계산 SQL 파일의 자리(${day}, ${partition}, ${raw_source})를 시험 값으로 바꾼다. */
    private static String script(String path) throws IOException {
        String sql = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        return String.join("\n", ClickHouseSqlRunner.splitStatements(sql))
                .replace("${day}", DAY)
                .replace("${partition}", PARTITION)
                .replace("${raw_source}", RAW_TABLE);
    }

    private void runScript(String path) throws IOException {
        String sql = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8)
                .replace("${day}", DAY)
                .replace("${partition}", PARTITION)
                .replace("${raw_source}", RAW_TABLE);
        ClickHouseSqlRunner.splitStatements(sql).forEach(jdbc::execute);
    }

    private void insertClickFacts(String minute, String ageGroup, long cnt) {
        clickHouseJdbc.update("INSERT INTO click_facts (campaign_id, minute, age_group, gender, cnt)"
                        + " VALUES (:campaignId, toDateTime(:minute), :ageGroup, 'female', :cnt)",
                Map.of("campaignId", CAMPAIGN_ID, "minute", minute, "ageGroup", ageGroup, "cnt", cnt));
    }

    private void insertRaw(String notificationId, String eventAt, String ageGroup) {
        clickHouseJdbc.update("INSERT INTO " + RAW_TABLE + " (notification_id, campaign_id, age_group, gender, event_at)"
                        + " VALUES (:id, :campaignId, :ageGroup, 'female', toDateTime(:eventAt))",
                Map.of("id", notificationId, "campaignId", CAMPAIGN_ID, "ageGroup", ageGroup, "eventAt", eventAt));
    }

    /** 11/21 하루의 합계. dateExpr 는 표마다 다른 날짜 컬럼 식이다. */
    private long sum(String table, String dateExpr) {
        Long total = jdbc.queryForObject(
                "SELECT sum(cnt) FROM " + table + " WHERE " + dateExpr + " = toDate('" + DAY + "')", Long.class);
        return total == null ? 0 : total;
    }

    private void clearAll() {
        for (String table : new String[] {"click_facts", "click_facts_hour", "click_facts_day",
                "click_facts_stage", "click_facts_hour_stage", "click_facts_day_stage"}) {
            jdbc.execute("TRUNCATE TABLE IF EXISTS " + table);
        }
        jdbc.execute("TRUNCATE TABLE IF EXISTS " + RAW_TABLE);
    }
}
