package com.techeer.backend.domain.commutesession.controller;

import com.techeer.backend.domain.commutesession.dto.CommuteSessionResponse;
import com.techeer.backend.domain.commutesession.dto.CommuteSessionStartRequest;
import com.techeer.backend.domain.commutesession.dto.CommuteSessionSummaryResponse;
import com.techeer.backend.domain.commutesession.dto.PlaybackEventRequest;
import com.techeer.backend.domain.commutesession.dto.PlaybackEventResponse;
import com.techeer.backend.domain.commutesession.service.CommuteSessionService;
import com.techeer.backend.global.resolver.MemberId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 통근 세션 API. 모두 로그인한 회원 본인의 통근만 다룬다. */
@RestController
@RequestMapping("/api/v1/commute-sessions")
@RequiredArgsConstructor
@Tag(name = "Commute Session", description = "통근 세션(시작·재생 이벤트·종료·취소·요약)")
public class CommuteSessionController {

    private final CommuteSessionService sessionService;

    @Operation(summary = "통근 시작",
            description = "내 통근 프로필로 통근을 시작한다. 진행 중인 통근이 있으면 409. 예상 소요시간은 프로필 경로에서 가져온다.")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public CommuteSessionResponse start(
            @MemberId Long memberId, @Valid @RequestBody CommuteSessionStartRequest request) {
        return sessionService.start(memberId, request);
    }

    @Operation(summary = "현재 통근 조회", description = "진행 중인 통근. 없으면 404. 앱이 다시 접속했을 때 복구하는 용도.")
    @GetMapping("/active")
    public CommuteSessionResponse active(@MemberId Long memberId) {
        return sessionService.getActive(memberId);
    }

    @Operation(summary = "재생 이벤트 기록",
            description = "통근 중 곡의 재생·건너뜀·다시 들음을 기록한다. 같은 eventId 로 다시 보내면 200(created=false), 새로 기록하면 201.")
    @PostMapping("/{sessionId}/playback-events")
    public ResponseEntity<PlaybackEventResponse> recordEvent(
            @MemberId Long memberId, @PathVariable Long sessionId, @Valid @RequestBody PlaybackEventRequest request) {
        PlaybackEventResponse response = sessionService.recordEvent(memberId, sessionId, request);
        return ResponseEntity.status(response.created() ? HttpStatus.CREATED : HttpStatus.OK).body(response);
    }

    @Operation(summary = "통근 종료", description = "진행 중인 통근을 끝내고 결과 요약을 돌려준다. 이미 끝났으면 409.")
    @PostMapping("/{sessionId}/complete")
    public CommuteSessionSummaryResponse complete(@MemberId Long memberId, @PathVariable Long sessionId) {
        return sessionService.complete(memberId, sessionId);
    }

    @Operation(summary = "통근 취소", description = "진행 중인 통근을 취소한다. 이미 끝났으면 409.")
    @PostMapping("/{sessionId}/cancel")
    public CommuteSessionResponse cancel(@MemberId Long memberId, @PathVariable Long sessionId) {
        return sessionService.cancel(memberId, sessionId);
    }

    @Operation(summary = "통근 결과 요약", description = "재생한 곡 수·곡별 요약·들은 시간. 진행 중이면 지금까지의 값.")
    @GetMapping("/{sessionId}/summary")
    public CommuteSessionSummaryResponse summary(@MemberId Long memberId, @PathVariable Long sessionId) {
        return sessionService.summary(memberId, sessionId);
    }
}
