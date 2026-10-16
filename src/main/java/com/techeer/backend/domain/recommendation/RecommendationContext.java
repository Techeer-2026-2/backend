package com.techeer.backend.domain.recommendation;

import com.techeer.backend.domain.commuteprofile.entity.CommuteType;
import com.techeer.backend.domain.weather.WeatherSnapshot;

/**
 * 추천 시점의 상황.
 *
 * @param weather 현재 날씨. 날씨 서비스가 실패했으면 null (그 경우 시간대와 통근 구분만으로 추천한다)
 */
public record RecommendationContext(WeatherSnapshot weather, TimeBand timeBand, CommuteType commuteType) {
}
