package com.techeer.backend;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 테스트에서 ClickHouse 노출, 클릭 분 표에 기록을 넣고 지우는 도우미.
 *
 * <p>분 표에 넣으면 MV 가 시간 표, 일 표에도 자동으로 더한다.
 * 주입: {@code @Autowired @Qualifier(ClickHouseConfig.QUALIFIER) NamedParameterJdbcTemplate clickHouseJdbc}
 */
public final class StatsFactsFixture {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private StatsFactsFixture() {
    }

    /** 노출 1건. ageGroup 이 null 이면 "unknown". */
    public static void addImpression(NamedParameterJdbcTemplate clickHouseJdbc, long campaignId, LocalDateTime at,
            String ageGroup) {
        insert(clickHouseJdbc, "impression_facts", campaignId, at, ageGroup);
    }

    /** 클릭 1건. ageGroup 이 null 이면 "unknown". */
    public static void addClick(NamedParameterJdbcTemplate clickHouseJdbc, long campaignId, LocalDateTime at,
            String ageGroup) {
        insert(clickHouseJdbc, "click_facts", campaignId, at, ageGroup);
    }

    /** 노출, 클릭의 분 표와 롤업 표를 모두 비운다. */
    public static void clear(NamedParameterJdbcTemplate clickHouseJdbc) {
        for (String table : new String[] {"impression_facts", "click_facts"}) {
            clickHouseJdbc.getJdbcTemplate().execute("TRUNCATE TABLE IF EXISTS " + table);
            clickHouseJdbc.getJdbcTemplate().execute("TRUNCATE TABLE IF EXISTS " + table + "_hour");
            clickHouseJdbc.getJdbcTemplate().execute("TRUNCATE TABLE IF EXISTS " + table + "_day");
        }
    }

    private static void insert(NamedParameterJdbcTemplate clickHouseJdbc, String table, long campaignId,
            LocalDateTime at, String ageGroup) {
        clickHouseJdbc.update("INSERT INTO " + table + " (campaign_id, minute, age_group, gender, cnt)"
                        + " VALUES (:campaignId, toStartOfMinute(toDateTime(:at)), :ageGroup, 'unknown', 1)",
                Map.of("campaignId", campaignId, "at", at.format(FORMAT),
                        "ageGroup", ageGroup == null ? "unknown" : ageGroup));
    }
}
