package com.techeer.backend.domain.tmap.controller;

import com.techeer.backend.domain.tmap.dto.RouteEstimateRequest;
import com.techeer.backend.domain.tmap.dto.RouteEstimateResponse;
import com.techeer.backend.domain.tmap.service.TmapDurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
@Tag(name = "Route", description = "경로 소요시간 미리보기")
public class RouteEstimateController {

    private final TmapDurationService tmapDurationService;

    @Operation(summary = "경로 소요시간 미리보기", description = "프로필 저장 없이 출발지/도착지/교통수단 기준 소요시간만 계산한다.")
    @PostMapping("/estimate")
    public RouteEstimateResponse estimate(@Valid @RequestBody RouteEstimateRequest request) {
        int durationMinutes = tmapDurationService.getDurationMinutes(
                request.startLongitude(), request.startLatitude(),
                request.endLongitude(), request.endLatitude(),
                request.transportMode());
        return new RouteEstimateResponse(durationMinutes);
    }
}
