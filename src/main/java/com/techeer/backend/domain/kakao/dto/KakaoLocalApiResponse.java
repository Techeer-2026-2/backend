package com.techeer.backend.domain.kakao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 카카오 키워드 장소 검색 API의 원본 응답 모양. 서비스 내부에서만 쓰고,
 * {@link PlaceSearchResponse}로 변환해서 내보낸다.
 */
public record KakaoLocalApiResponse(List<Document> documents) {

    public record Document(
            @JsonProperty("place_name") String placeName,
            @JsonProperty("address_name") String addressName,
            String x,
            String y) {
    }
}
