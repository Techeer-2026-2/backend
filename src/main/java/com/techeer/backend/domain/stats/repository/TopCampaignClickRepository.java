package com.techeer.backend.domain.stats.repository;

import com.techeer.backend.global.config.ClickHouseConfig;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * ClickHouse click_facts(분 표)에서 기간 안의 캠페인별 클릭 수를 많은 순으로 읽는다 (고도화 2026-11).
 *
 * <p>시각은 문자열("yyyy-MM-dd HH:mm:ss")로 넘겨서 서버, 드라이버 시간대 설정과 상관없이 같은 값으로 비교한다.
 */
@Repository
public class TopCampaignClickRepository {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NamedParameterJdbcTemplate clickHouseJdbc;

    public TopCampaignClickRepository(
            @Qualifier(ClickHouseConfig.QUALIFIER) NamedParameterJdbcTemplate clickHouseJdbc) {
        this.clickHouseJdbc = clickHouseJdbc;
    }

    /**
     * 주어진 캠페인 중 [start, end) 에 클릭이 있는 캠페인을 클릭 많은 순(같으면 ID 작은 순)으로 limit 개 돌려준다.
     *
     * @return 캠페인 ID 와 클릭 수 (순서 유지)
     */
    public Map<Long, Long> findTopClicks(Collection<Long> campaignIds, LocalDateTime start, LocalDateTime end,
            int limit) {
        Map<Long, Long> clicksById = new LinkedHashMap<>();
        if (campaignIds.isEmpty()) {
            return clicksById;
        }
        clickHouseJdbc.query("""
                SELECT campaign_id, sum(cnt) AS clicks
                FROM click_facts
                WHERE campaign_id IN (:campaignIds)
                  AND minute >= toDateTime(:start) AND minute < toDateTime(:end)
                GROUP BY campaign_id
                HAVING clicks > 0
                ORDER BY clicks DESC, campaign_id ASC
                LIMIT :limit
                """,
                Map.of("campaignIds", campaignIds, "start", start.format(FORMAT), "end", end.format(FORMAT),
                        "limit", limit),
                rs -> {
                    clicksById.put(rs.getLong("campaign_id"), rs.getLong("clicks"));
                });
        return clicksById;
    }
}
