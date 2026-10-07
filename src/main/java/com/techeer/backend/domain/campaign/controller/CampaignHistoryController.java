package com.techeer.backend.domain.campaign.controller;

import com.techeer.backend.domain.auth.resolver.AdvertiserId;
import com.techeer.backend.domain.campaign.dto.CampaignHistoryItem;
import com.techeer.backend.domain.campaign.service.CampaignHistoryService;
import com.techeer.backend.global.config.SwaggerConfig;
import com.techeer.backend.global.dto.PageResponse;
import com.techeer.backend.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지난 캠페인 이력. /campaigns/{id} 와 주소가 겹치지만 Spring 은 고정 글자(history)를 변수({id})보다 먼저 맞춘다.
 */
@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campaign History", description = "지난 캠페인 이력")
public class CampaignHistoryController {

    private final CampaignHistoryService campaignHistoryService;

    /**
     * 로그인한 광고주의 지난 캠페인과 결과 요약을 페이지 단위로 조회한다. 광고주 ID 는 access token 에서 꺼낸다.
     *
     * @param advertiserId access token 에서 꺼낸 광고주 ID
     * @param page 페이지 번호(0부터, 기본 0)
     * @param size 페이지 크기(1~100, 기본 20)
     * @return 지난 캠페인 목록 페이지
     */
    @Operation(
            summary = "캠페인 이력 조회",
            description = "종료된 내 캠페인(상태 ENDED 이거나 종료 시각이 지난 것)과 노출·클릭·클릭률을 "
                    + "최근에 끝난 순으로 돌려준다. 초안(DRAFT)과 삭제한 캠페인은 제외한다.",
            security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "토큰이 없거나(AUTH_REQUIRED) 올바르지 않음(INVALID_TOKEN)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "없거나 탈퇴한 광고주",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/history")
    public PageResponse<CampaignHistoryItem> getHistory(
            @AdvertiserId Long advertiserId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return campaignHistoryService.getHistory(advertiserId, page, size);
    }
}
