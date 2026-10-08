package com.techeer.backend.domain.kakao.dto;

/**
 * 장소 검색 결과 한 건. 통근 프로필의 출발지/도착지(Location)와 필드 이름을 맞춰
 * 프론트가 선택한 장소를 그대로 통근 프로필 등록 요청에 옮겨 쓸 수 있게 한다.
 */
public record PlaceSearchResponse(String placeName, String addressName, double latitude, double longitude) {
}
