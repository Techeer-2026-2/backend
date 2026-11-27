package com.techeer.backend.domain.stats.repository;

import com.techeer.backend.global.config.ClickHouseConfig;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * ClickHouse 집계 표에서 노출, 클릭 수를 구간별, 연령대별로 읽는다 (고도화 2026-11).
 *
 * <p>시각은 문자열("yyyy-MM-dd HH:mm:ss")로 주고받는다. 저장도 같은 형식의 한국 시각이라
 * 드라이버나 서버의 시간대 설정과 상관없이 같은 값이 나온다.
 * SummingMergeTree 는 병합 전에 같은 키가 여러 행으로 남아 있을 수 있어서 항상 sum(cnt) 로 읽는다.
 * 실시간 값이라 재계산 전까지는 중복이 섞일 수 있다 (잠정).
 */
@Repository
public class StatsFactsRepository {

    /** 노출과 클릭 중 어느 쪽을 읽을지. 표 이름 앞부분이 다르다. */
    public enum Fact {
        IMPRESSION("impression_facts"),
        CLICK("click_facts");

        private final String minuteTable;

        Fact(String minuteTable) {
            this.minuteTable = minuteTable;
        }
    }

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NamedParameterJdbcTemplate clickHouseJdbc;

    public StatsFactsRepository(@Qualifier(ClickHouseConfig.QUALIFIER) NamedParameterJdbcTemplate clickHouseJdbc) {
        this.clickHouseJdbc = clickHouseJdbc;
    }

    /**
     * [start, end) 구간의 수를 시간("hour") 또는 일("day") 단위로 묶어 돌려준다. 기록이 없는 구간은 결과에 없다.
     *
     * <p>start, end 가 단위의 경계(정각, 자정)에 딱 맞으면 미리 묶어 둔 시간 표, 일 표를 읽는다.
     * 경계에 맞지 않으면 분 표에서 묶는다 (구간 앞뒤의 일부만 세야 하므로).
     */
    public Map<LocalDateTime, Long> countByBucket(Fact fact, Long campaignId, String unit,
            LocalDateTime start, LocalDateTime end) {
        boolean hour = "hour".equals(unit);
        ChronoUnit step = hour ? ChronoUnit.HOURS : ChronoUnit.DAYS;
        boolean aligned = start.truncatedTo(step).equals(start) && end.truncatedTo(step).equals(end);

        String sql;
        if (aligned && hour) {
            sql = "SELECT formatDateTime(hour, '%Y-%m-%d %H:%i:%S') AS bucket, sum(cnt) AS total"
                    + " FROM " + fact.minuteTable + "_hour"
                    + " WHERE campaign_id = :campaignId AND hour >= toDateTime(:start) AND hour < toDateTime(:end)"
                    + " GROUP BY bucket";
        } else if (aligned) {
            sql = "SELECT formatDateTime(toDateTime(day), '%Y-%m-%d %H:%i:%S') AS bucket, sum(cnt) AS total"
                    + " FROM " + fact.minuteTable + "_day"
                    + " WHERE campaign_id = :campaignId AND day >= toDate(:start) AND day < toDate(:end)"
                    + " GROUP BY bucket";
        } else {
            String truncate = hour ? "toStartOfHour(minute)" : "toStartOfDay(minute)";
            sql = "SELECT formatDateTime(" + truncate + ", '%Y-%m-%d %H:%i:%S') AS bucket, sum(cnt) AS total"
                    + " FROM " + fact.minuteTable
                    + " WHERE campaign_id = :campaignId AND minute >= toDateTime(:start) AND minute < toDateTime(:end)"
                    + " GROUP BY bucket";
        }

        Map<LocalDateTime, Long> counts = new HashMap<>();
        clickHouseJdbc.query(sql, params(campaignId, start, end), rs -> {
            counts.put(LocalDateTime.parse(rs.getString("bucket"), FORMAT), rs.getLong("total"));
        });
        return counts;
    }

    /**
     * [start, end) 구간의 수를 연령대("10s"~"50s", "unknown")별로 돌려준다. 기록이 없는 연령대는 결과에 없다.
     */
    public Map<String, Long> countByAgeGroup(Fact fact, Long campaignId, LocalDateTime start, LocalDateTime end) {
        String sql = "SELECT toString(age_group) AS age, sum(cnt) AS total"
                + " FROM " + fact.minuteTable
                + " WHERE campaign_id = :campaignId AND minute >= toDateTime(:start) AND minute < toDateTime(:end)"
                + " GROUP BY age";
        Map<String, Long> counts = new HashMap<>();
        clickHouseJdbc.query(sql, params(campaignId, start, end), rs -> {
            counts.put(rs.getString("age"), rs.getLong("total"));
        });
        return counts;
    }

    private static Map<String, Object> params(Long campaignId, LocalDateTime start, LocalDateTime end) {
        return Map.of("campaignId", campaignId, "start", start.format(FORMAT), "end", end.format(FORMAT));
    }
}
