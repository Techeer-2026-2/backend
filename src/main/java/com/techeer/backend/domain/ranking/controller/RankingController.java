package com.techeer.backend.domain.ranking.controller;

import com.techeer.backend.domain.ranking.dto.RankingDtos.ChartResponse;
import com.techeer.backend.domain.ranking.dto.RankingDtos.Data;
import com.techeer.backend.domain.ranking.service.RankingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rankings/tracks")
@RequiredArgsConstructor
@Tag(name = "음악 랭킹")
public class RankingController {
    private final RankingQueryService service;

    @GetMapping("/nearby")
    @Operation(summary = "최근 완료된 시간 배치의 주변 차트 조회", description = "주변 → H3 r7 → 도시 → 전체 순서로 조회합니다.")
    public Data<ChartResponse> nearby(@RequestParam double lat, @RequestParam double lng,
                                     @RequestParam(defaultValue = "20") int limit) {
        return new Data<>(service.nearby(lat, lng, limit));
    }

    @GetMapping("/global")
    @Operation(summary = "최근 완료된 시간 배치의 전체 차트 조회")
    public Data<ChartResponse> global(@RequestParam(defaultValue = "20") int limit) {
        return new Data<>(service.global(limit));
    }
}
