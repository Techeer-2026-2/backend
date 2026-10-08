package com.techeer.backend.domain.stats.controller;

import com.techeer.backend.domain.auth.resolver.AdvertiserId;
import com.techeer.backend.domain.stats.service.CampaignStatsStreamService;
import com.techeer.backend.global.config.SwaggerConfig;
import com.techeer.backend.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Stats", description = "캠페인 통계")
public class CampaignStatsStreamController {

    private final CampaignStatsStreamService campaignStatsStreamService;

    /**
     * 로그인한 광고주 본인 캠페인의 노출·클릭 통계를 SSE(text/event-stream)로 계속 보낸다.
     *
     * <p>브라우저 기본 {@code EventSource} 는 Authorization 헤더를 보낼 수 없어서, 프론트는 헤더를 보낼 수 있는
     * fetch 기반 SSE 라이브러리(예: @microsoft/fetch-event-source)로 연결해야 한다.
     * 인증은 연결을 시작할 때 한 번 확인하며, 연결은 최대 30분(access token 수명과 같게) 유지된다.
     *
     * <p>운영에서는 앞단의 nginx 가 기본으로 응답을 모았다가 보내서 이벤트가 실시간으로 도착하지 않는다.
     * X-Accel-Buffering: no 헤더를 붙이면 nginx 가 이 응답만 버퍼링을 끈다(nginx 설정을 바꾸지 않아도 된다).
     *
     * @param advertiserId access token 에서 꺼낸 광고주 ID
     * @param id 캠페인 ID
     * @param response 버퍼링 방지 헤더를 붙이기 위한 응답
     * @return 'stats' 이벤트를 흘려보내는 SseEmitter
     */
    @Operation(
            summary = "실시간 통계 구독(SSE)",
            description = "내 캠페인의 현재 노출·클릭 수를 바로 보내고, 이후 몇 초마다 다시 보낸다. "
                    + "이벤트 이름은 stats, data 는 JSON 이다. Authorization 헤더가 필요해서 기본 EventSource 는 쓸 수 없고 "
                    + "fetch 기반 SSE 라이브러리로 연결한다. 연결은 최대 30분 유지되며 끊기면 다시 연결한다.",
            security = @SecurityRequirement(name = SwaggerConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "text/event-stream 으로 계속 전송")
    @ApiResponse(responseCode = "401", description = "토큰이 없거나(AUTH_REQUIRED) 올바르지 않음(INVALID_TOKEN)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "없거나 삭제된 캠페인, 또는 다른 광고주의 캠페인",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/campaigns/{id}/stats/stream")
    public SseEmitter stream(
            @AdvertiserId Long advertiserId,
            @PathVariable("id") Long id,
            HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return campaignStatsStreamService.subscribe(advertiserId, id);
    }
}
