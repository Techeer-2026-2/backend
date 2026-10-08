package com.techeer.backend.domain.kakao.service;

import com.techeer.backend.domain.kakao.dto.KakaoLocalApiResponse;
import com.techeer.backend.domain.kakao.dto.PlaceSearchResponse;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class KakaoPlaceService {

    private final RestClient kakaoRestClient;

    public List<PlaceSearchResponse> search(String keyword) {
        try {
            KakaoLocalApiResponse response = kakaoRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/keyword.json")
                            .queryParam("query", keyword)
                            .build())
                    .retrieve()
                    .body(KakaoLocalApiResponse.class);

            if (response == null || response.documents() == null) {
                throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
            }

            return response.documents().stream()
                    .map(this::toPlaceSearchResponse)
                    .toList();
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }

    private PlaceSearchResponse toPlaceSearchResponse(KakaoLocalApiResponse.Document doc) {
        try {
            return new PlaceSearchResponse(
                    doc.placeName(),
                    doc.addressName(),
                    Double.parseDouble(doc.y()),
                    Double.parseDouble(doc.x()));
        } catch (NullPointerException | NumberFormatException e) {
            // 좌표가 없거나 숫자가 아니면 외부 응답 오류로 본다.
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }
}
