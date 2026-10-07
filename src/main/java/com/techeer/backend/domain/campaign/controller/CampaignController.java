package com.techeer.backend.domain.campaign.controller;

import com.techeer.backend.domain.auth.resolver.AdvertiserId;
import com.techeer.backend.domain.campaign.dto.CampaignCreateRequest;
import com.techeer.backend.domain.campaign.dto.CampaignProgress;
import com.techeer.backend.domain.campaign.dto.CampaignResponse;
import com.techeer.backend.domain.campaign.service.CampaignService;
import com.techeer.backend.global.config.SwaggerConfig;
import com.techeer.backend.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Campaign", description = "컴백 광고 배너 캠페인 관리")
public class CampaignController {

    private final CampaignService campaignService;

    @Operation(
            summary = "캠페인 등록",
            description = "컴백 광고 배너 캠페인을 등록한다. linkUrl 을 비우면 배너 탭 자체를 클릭으로 집계한다.",
            security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "등록됨")
    @ApiResponse(responseCode = "401", description = "토큰이 없거나(AUTH_REQUIRED) 올바르지 않음(INVALID_TOKEN)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "400", description = "요청 값 오류 또는 노출 기간 오류",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/campaigns")
    public ResponseEntity<CampaignResponse> createCampaign(
            @AdvertiserId Long advertiserId,
            @Valid @RequestBody CampaignCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.createCampaign(advertiserId, request));
    }

    @Operation(
            summary = "내 캠페인 목록 조회",
            description = "내 캠페인을 최근 등록순으로 돌려준다. status 를 주면 해당 진행 상태만 조회한다.",
            security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "캠페인 목록")
    @ApiResponse(responseCode = "401", description = "토큰이 없거나(AUTH_REQUIRED) 올바르지 않음(INVALID_TOKEN)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "400", description = "요청 값 오류",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/campaigns")
    public ResponseEntity<List<CampaignResponse>> getMyCampaigns(
            @AdvertiserId Long advertiserId,
            @Parameter(description = "진행 상태 필터 (PENDING: 대기, ONGOING: 진행중, ENDED: 종료). 비우면 전체")
            @RequestParam(required = false) CampaignProgress status) {
        return ResponseEntity.ok(campaignService.getMyCampaigns(advertiserId, status));
    }
}
