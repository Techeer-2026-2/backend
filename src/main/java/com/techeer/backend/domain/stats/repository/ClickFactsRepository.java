package com.techeer.backend.domain.stats.repository;

import com.techeer.backend.global.config.ClickHouseConfig;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * ClickHouse click_facts(분 표)에서 캠페인별 누적 클릭 수를 읽는다 (고도화 2026-11).
 *
 * <p>클릭은 더 이상 campaign_stats.click_count 를 갱신하지 않으므로 누적 클릭은 여기서 읽는다.
 * 실시간 값이라 재계산 전까지는 중복이 섞일 수 있다 (잠정).
 * SummingMergeTree 는 병합 전에 같은 키가 여러 행으로 남아 있을 수 있어서 항상 sum(cnt) 로 읽는다.
 */
@Repository
public class ClickFactsRepository {

    private final NamedParameterJdbcTemplate clickHouseJdbc;

    public ClickFactsRepository(@Qualifier(ClickHouseConfig.QUALIFIER) NamedParameterJdbcTemplate clickHouseJdbc) {
        this.clickHouseJdbc = clickHouseJdbc;
    }

    /**
     * 캠페인 1건의 누적 클릭 수. 클릭이 없으면 0.
     */
    public long sumClicks(Long campaignId) {
        Long clicks = clickHouseJdbc.queryForObject(
                "SELECT sum(cnt) FROM click_facts WHERE campaign_id = :campaignId",
                Map.of("campaignId", campaignId), Long.class);
        return clicks == null ? 0 : clicks;
    }

    /**
     * 여러 캠페인의 누적 클릭 수를 한 번에 읽는다. 클릭이 없는 캠페인은 결과에 없으므로 getOrDefault(id, 0L) 로 쓴다.
     */
    public Map<Long, Long> sumClicksByCampaign(Collection<Long> campaignIds) {
        Map<Long, Long> clicksById = new HashMap<>();
        if (campaignIds.isEmpty()) {
            return clicksById;
        }
        clickHouseJdbc.query(
                "SELECT campaign_id, sum(cnt) AS clicks FROM click_facts WHERE campaign_id IN (:campaignIds) GROUP BY campaign_id",
                Map.of("campaignIds", campaignIds),
                rs -> {
                    clicksById.put(rs.getLong("campaign_id"), rs.getLong("clicks"));
                });
        return clicksById;
    }
}
