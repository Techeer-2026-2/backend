package com.techeer.backend.domain.tmap.service;

import com.techeer.backend.domain.tmap.TransportMode;
import com.techeer.backend.domain.tmap.dto.TmapCarRouteRequest;
import com.techeer.backend.domain.tmap.dto.TmapCarRouteResponse;
import com.techeer.backend.domain.tmap.dto.TmapTransitRouteRequest;
import com.techeer.backend.domain.tmap.dto.TmapTransitRouteResponse;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class TmapDurationService {

    private final RestClient tmapRestClient;

    public int getDurationMinutes(double startX, double startY, double endX, double endY, TransportMode mode) {
        int totalSeconds = switch (mode) {
            case CAR -> fetchCarDurationSeconds(startX, startY, endX, endY);
            case PUBLIC_TRANSIT -> fetchTransitDurationSeconds(startX, startY, endX, endY);
        };
        return totalSeconds / 60;
    }

    private int fetchCarDurationSeconds(double startX, double startY, double endX, double endY) {
        try {
            TmapCarRouteResponse response = tmapRestClient.post()
                    .uri("/tmap/routes?version=1")
                    .body(new TmapCarRouteRequest(startX, startY, endX, endY))
                    .retrieve()
                    .body(TmapCarRouteResponse.class);

            return response.features().get(0).properties().totalTime();
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }

    private int fetchTransitDurationSeconds(double startX, double startY, double endX, double endY) {
        try {
            TmapTransitRouteResponse response = tmapRestClient.post()
                    .uri("/transit/routes")
                    .body(TmapTransitRouteRequest.of(startX, startY, endX, endY))
                    .retrieve()
                    .body(TmapTransitRouteResponse.class);

            return response.metaData().plan().itineraries().get(0).totalTime();
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }
}
