package com.techeer.backend.domain.ranking.controller;

import com.techeer.backend.domain.ranking.dto.RankingDtos.Data;
import com.techeer.backend.domain.ranking.dto.RankingDtos.EventRequest;
import com.techeer.backend.domain.ranking.dto.RankingDtos.EventResult;
import com.techeer.backend.domain.ranking.dto.RankingDtos.LocationRequest;
import com.techeer.backend.domain.ranking.dto.RankingDtos.SessionResult;
import com.techeer.backend.domain.ranking.dto.RankingDtos.StartRequest;
import com.techeer.backend.domain.ranking.dto.RankingDtos.Track;
import com.techeer.backend.domain.ranking.dto.RankingDtos.TrackInput;
import com.techeer.backend.domain.ranking.service.PlaybackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "음악 청취 이벤트", description = "P1 내부 수집 API. 사용자 인증 연동 전에는 신뢰된 클라이언트만 접근해야 합니다.")
public class PlaybackController {
    private final PlaybackService service;

    @PostMapping("/locations")
    @Operation(summary = "사용자의 최신 위치 저장")
    public ResponseEntity<Void> location(@Valid @RequestBody LocationRequest request) {
        service.updateLocation(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/playback/sessions")
    @Operation(summary = "재생 시작 위치를 고정한 세션 생성")
    public ResponseEntity<Data<SessionResult>> start(@Valid @RequestBody StartRequest request) {
        SessionResult result = service.start(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(new Data<>(result));
    }

    @PostMapping("/playback/events")
    @Operation(summary = "완주 또는 스킵 이벤트 저장", description = "같은 eventId 재시도는 200. 동일 세션의 다른 종료 이벤트는 409.")
    public ResponseEntity<Data<EventResult>> event(@Valid @RequestBody EventRequest request) {
        EventResult result = service.record(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(new Data<>(result));
    }

    @PutMapping("/music/tracks/{trackId}")
    @Operation(summary = "곡 메타데이터 등록·갱신 (내부 카탈로그 연동용)")
    public Data<Track> saveTrack(@PathVariable String trackId, @Valid @RequestBody TrackInput request) {
        return new Data<>(service.saveTrack(trackId, request));
    }

    @GetMapping("/music/tracks/{trackId}")
    @Operation(summary = "곡 상세 조회")
    public Data<Track> track(@PathVariable String trackId) {
        return new Data<>(service.track(trackId));
    }
}
