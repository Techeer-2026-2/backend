package com.techeer.backend.domain.tmap.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * TMAP 보행자 길찾기 응답. 자동차/대중교통과 다르게 전체 소요시간이 features 배열 안에,
 * 출발점(pointType "SP")을 나타내는 Point 타입 Feature의 properties.totalTime 에 들어있다.
 * 나머지 Feature(중간 경유점, 구간 LineString)는 totalTime 이 없다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmapPedestrianRouteResponse(List<Feature> features) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Feature(Properties properties) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Properties(Integer totalTime) {
    }
}
