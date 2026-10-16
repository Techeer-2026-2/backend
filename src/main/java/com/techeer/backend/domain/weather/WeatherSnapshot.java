package com.techeer.backend.domain.weather;

/**
 * 추천에 쓰는 현재 날씨. {@link WeatherCondition} 4가지로는 안개·폭풍·기온을 구분할 수 없어 따로 담는다.
 *
 * @param temperatureCelsius 섭씨 기온. 응답에 없으면 null
 * @param hazy 안개·연무·먼지 등 시야가 흐린 날씨
 * @param stormy 천둥번개·돌풍 등 거친 날씨
 */
public record WeatherSnapshot(WeatherCondition condition, Double temperatureCelsius, boolean hazy, boolean stormy) {
}
