package com.techeer.backend.domain.recommendation;

import com.techeer.backend.domain.ranking.dto.RankingDtos.ChartResponse;
import com.techeer.backend.domain.ranking.dto.RankingDtos.Item;
import com.techeer.backend.domain.ranking.service.RankingCalculator;
import com.techeer.backend.domain.ranking.service.RankingQueryService;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 주변 랭킹 차트({@link RankingQueryService#nearby})를 추천에 쓸 곡별 가산점으로 바꾼다.
 *
 * <p>가산점 = {@link #MAX_BOOST} × 차트 범위 강도 × (곡의 청취자 수 ÷ 차트 1위의 청취자 수).
 * 차트가 내 셀에서 멀리 fallback 될수록(상위 셀 → 도시 → 전체) 강도를 낮춘다. 전체 인기곡은 "내 주변"의 정보가 아니기 때문이다.
 * 데이터가 부족하거나 아직 집계 전이어서 차트가 비어 있으면 아무 영향도 주지 않는다.
 *
 * <p>랭킹 곡 ID 는 클라이언트가 정한 문자열이라 우리 곡(iTunes 숫자 ID)과 연결하는 규칙이 팀에서 정해지지 않았다.
 * 그래서 {@code itunes-123} 과 {@code 123} 두 형식을 모두 같은 곡으로 본다. 그 밖의 형식은 연결하지 못하고 건너뛴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NearbyPopularityProvider {

    /** 가산점의 최댓값. 적합도가 최대 이 비율만큼 올라간다(예: 0.5 → 1.5배). */
    static final double MAX_BOOST = 0.5;
    private static final String ITUNES_PREFIX = "itunes-";
    private static final Map<String, Double> SCOPE_STRENGTH = Map.of(
            "NEARBY", 1.0,
            "PARENT", 0.7,
            "CITY", 0.5,
            "GLOBAL", 0.25);

    private final RankingQueryService rankingQueryService;

    /**
     * 위치 주변 인기곡 가산점을 구한다. 랭킹 조회가 어떤 이유로든 실패해도 예외를 던지지 않고 영향 없음으로 돌려준다
     * (추천 자체가 막히면 안 된다).
     */
    public NearbyPopularity find(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return NearbyPopularity.none();
        }
        try {
            return toPopularity(rankingQueryService.nearby(latitude, longitude, RankingCalculator.MAX_ITEMS));
        } catch (RuntimeException e) {
            log.warn("주변 랭킹 조회에 실패해 인기곡 가산점 없이 추천합니다.", e);
            return NearbyPopularity.none();
        }
    }

    static NearbyPopularity toPopularity(ChartResponse chart) {
        double strength = SCOPE_STRENGTH.getOrDefault(chart.scope(), 0.0);
        if (!"READY".equals(chart.status()) || strength == 0.0 || chart.items().isEmpty()) {
            return NearbyPopularity.none();
        }
        long maxListeners = chart.items().stream().mapToLong(Item::uniqueListenerCount).max().orElse(0);
        if (maxListeners <= 0) {
            return NearbyPopularity.none();
        }
        Map<Long, Double> boosts = new HashMap<>();
        for (Item item : chart.items()) {
            Long itunesId = itunesIdOf(item.track().id());
            if (itunesId == null || item.uniqueListenerCount() <= 0) {
                continue;
            }
            double boost = MAX_BOOST * strength * item.uniqueListenerCount() / maxListeners;
            boosts.merge(itunesId, boost, Math::max);
        }
        return boosts.isEmpty() ? NearbyPopularity.none()
                : new NearbyPopularity(Map.copyOf(boosts), chart.scope(), chart.locationLabel());
    }

    /** 랭킹 곡 ID 에서 iTunes 곡 ID 를 꺼낸다. 연결할 수 없는 형식이면 null. */
    static Long itunesIdOf(String rankingTrackId) {
        if (rankingTrackId == null) {
            return null;
        }
        String digits = rankingTrackId.trim().toLowerCase(Locale.ROOT);
        if (digits.startsWith(ITUNES_PREFIX)) {
            digits = digits.substring(ITUNES_PREFIX.length());
        }
        try {
            long id = Long.parseLong(digits);
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
