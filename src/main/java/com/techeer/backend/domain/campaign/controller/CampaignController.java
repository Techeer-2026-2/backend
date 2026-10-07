package com.techeer.backend.domain.campaign.controller;

import com.techeer.backend.domain.campaign.dto.CampaignCreateRequest;
import com.techeer.backend.domain.campaign.dto.CampaignDetailResponse;
import com.techeer.backend.domain.campaign.dto.CampaignProgress;
import com.techeer.backend.domain.campaign.dto.CampaignResponse;
import com.techeer.backend.domain.campaign.dto.CampaignUpdateRequest;
import com.techeer.backend.domain.campaign.service.CampaignService;
import com.techeer.backend.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    // userId 는 광고주 로그인(#16)이 붙기 전까지 요청 파라미터로 받는다.
    @Operation(
            summary = "캠페인 등록",
            description = "컴백 광고 배너 캠페인을 등록한다. linkUrl 을 비우면 배너 탭 자체를 클릭으로 집계한다.")
    @ApiResponse(responseCode = "201", description = "등록됨")
    @ApiResponse(responseCode = "400", description = "요청 값 오류 또는 노출 기간 오류",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/campaigns")
    public ResponseEntity<CampaignResponse> createCampaign(
            @Parameter(description = "캠페인을 등록하는 광고주 ID", example = "1")
            @RequestParam Long userId,
            @Valid @RequestBody CampaignCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.createCampaign(userId, request));
    }

    @Operation(
            summary = "내 캠페인 목록 조회",
            description = "내 캠페인을 최근 등록순으로 돌려준다. status 를 주면 해당 진행 상태만 조회한다.")
    @ApiResponse(responseCode = "200", description = "캠페인 목록")
    @ApiResponse(responseCode = "400", description = "요청 값 오류",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/campaigns")
    public ResponseEntity<List<CampaignResponse>> getMyCampaigns(
            @Parameter(description = "조회하는 광고주 ID", example = "1")
            @RequestParam Long userId,
            @Parameter(description = "진행 상태 필터 (PENDING: 대기, ONGOING: 진행중, ENDED: 종료). 비우면 전체")
            @RequestParam(required = false) CampaignProgress status) {
        return ResponseEntity.ok(campaignService.getMyCampaigns(userId, status));
    }

    @Operation(
            summary = "캠페인 상세 조회",
            description = "캠페인 1건을 노출수·클릭수와 함께 돌려준다. 통계 화면에서 사용한다.")
    @ApiResponse(responseCode = "200", description = "캠페인 상세")
    @ApiResponse(responseCode = "403", description = "내 캠페인이 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "존재하지 않거나 삭제된 캠페인",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/campaigns/{campaignId}")
    public ResponseEntity<CampaignDetailResponse> getCampaign(
            @PathVariable Long campaignId,
            @Parameter(description = "조회하는 광고주 ID", example = "1")
            @RequestParam Long userId) {
        return ResponseEntity.ok(campaignService.getCampaign(userId, campaignId));
    }

    @Operation(
            summary = "캠페인 수정",
            description = "바꿀 필드만 보낸다. 대기 상태는 전체 필드를, 진행중은 title/body/imageUrl 만 수정할 수 있다. "
                    + "종료된 캠페인은 수정할 수 없다.")
    @ApiResponse(responseCode = "200", description = "수정된 캠페인")
    @ApiResponse(responseCode = "400", description = "요청 값 오류 또는 노출 기간 오류",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "내 캠페인이 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "존재하지 않거나 삭제된 캠페인",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "현재 상태에서 수정할 수 없는 필드 또는 종료된 캠페인",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/campaigns/{campaignId}")
    public ResponseEntity<CampaignDetailResponse> updateCampaign(
            @PathVariable Long campaignId,
            @Parameter(description = "수정하는 광고주 ID", example = "1")
            @RequestParam Long userId,
            @Valid @RequestBody CampaignUpdateRequest request) {
        return ResponseEntity.ok(campaignService.updateCampaign(userId, campaignId, request));
    }

    @Operation(
            summary = "캠페인 삭제",
            description = "캠페인을 삭제한다. 노출·클릭 기록은 남지만 배너 노출과 목록에서는 빠진다.")
    @ApiResponse(responseCode = "204", description = "삭제됨", content = @Content)
    @ApiResponse(responseCode = "403", description = "내 캠페인이 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "존재하지 않거나 이미 삭제된 캠페인",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/campaigns/{campaignId}")
    public ResponseEntity<Void> deleteCampaign(
            @PathVariable Long campaignId,
            @Parameter(description = "삭제하는 광고주 ID", example = "1")
            @RequestParam Long userId) {
        campaignService.deleteCampaign(userId, campaignId);
        return ResponseEntity.noContent().build();
    }
}
