package com.techeer.backend;

import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 테스트에서 ClickHouse click_facts 에 클릭을 넣고 지우는 도우미.
 *
 * <p>누적 클릭 수는 click_facts 에서 읽으므로, 클릭이 있는 상황은 이 도우미로 만든다.
 * 주입: {@code @Autowired @Qualifier(ClickHouseConfig.QUALIFIER) NamedParameterJdbcTemplate clickHouseJdbc}
 */
public final class ClickFactsFixture {

    private ClickFactsFixture() {
    }

    /** 캠페인에 클릭 count 개를 넣는다 (지금 시각의 분 단위 행 1개). */
    public static void addClicks(NamedParameterJdbcTemplate clickHouseJdbc, long campaignId, long count) {
        clickHouseJdbc.update("""
                INSERT INTO click_facts (campaign_id, minute, age_group, gender, cnt)
                VALUES (:campaignId, toStartOfMinute(now()), 'unknown', 'unknown', :count)
                """, Map.of("campaignId", campaignId, "count", count));
    }

    /** 분 표와 롤업 표의 클릭을 모두 지운다. */
    public static void clear(NamedParameterJdbcTemplate clickHouseJdbc) {
        clickHouseJdbc.getJdbcTemplate().execute("TRUNCATE TABLE IF EXISTS click_facts");
        clickHouseJdbc.getJdbcTemplate().execute("TRUNCATE TABLE IF EXISTS click_facts_hour");
        clickHouseJdbc.getJdbcTemplate().execute("TRUNCATE TABLE IF EXISTS click_facts_day");
    }
}
