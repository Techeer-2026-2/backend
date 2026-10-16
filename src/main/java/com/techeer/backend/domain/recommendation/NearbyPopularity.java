package com.techeer.backend.domain.recommendation;

import java.util.Map;

/**
 * 출발지 주변 인기곡 차트에서 뽑아 낸, 곡별 가산점. 추천 적합도에 {@code × (1 + boost)} 로 반영한다.
 *
 * @param boostByItunesId iTunes 곡 ID 별 가산점 (0 초과 {@link NearbyPopularityProvider#MAX_BOOST} 이하)
 * @param scope 쓴 차트의 범위: NEARBY(내 셀) / PARENT(상위 셀) / CITY(도시) / GLOBAL(전체). 쓰지 못했으면 NONE
 * @param locationLabel 차트 이름(예: "성수동 주변"). 쓰지 못했으면 null
 */
public record NearbyPopularity(Map<Long, Double> boostByItunesId, String scope, String locationLabel) {

    private static final NearbyPopularity NONE = new NearbyPopularity(Map.of(), "NONE", null);

    /** 위치를 모르거나 차트가 없거나 호출이 실패했을 때. 추천에 아무 영향도 주지 않는다. */
    public static NearbyPopularity none() {
        return NONE;
    }

    public boolean applied() {
        return !boostByItunesId.isEmpty();
    }

    public double boostOf(Long itunesTrackId) {
        return itunesTrackId == null ? 0.0 : boostByItunesId.getOrDefault(itunesTrackId, 0.0);
    }
}
