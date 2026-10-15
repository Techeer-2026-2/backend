package com.techeer.backend.domain.playlist.controller;

import com.techeer.backend.domain.playlist.dto.PlaylistDtos.ActiveListenersResponse;
import com.techeer.backend.domain.playlist.dto.PlaylistDtos.CreateRequest;
import com.techeer.backend.domain.playlist.dto.PlaylistDtos.Data;
import com.techeer.backend.domain.playlist.dto.PlaylistDtos.HeartbeatResponse;
import com.techeer.backend.domain.playlist.dto.PlaylistDtos.ListeningHeartbeatRequest;
import com.techeer.backend.domain.playlist.dto.PlaylistDtos.PlaylistResponse;
import com.techeer.backend.domain.playlist.dto.PlaylistDtos.PlaylistSummary;
import com.techeer.backend.domain.playlist.dto.PlaylistDtos.ReplaceTracksRequest;
import com.techeer.backend.domain.playlist.dto.PlaylistDtos.ShareResponse;
import com.techeer.backend.domain.playlist.service.PlaylistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "음악 앱 플레이리스트 공유 및 주변 청취 인원")
public class PlaylistController {
    private final PlaylistService service;

    @PostMapping("/playlists")
    @Operation(summary = "내 플레이리스트 생성", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Data<PlaylistResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new Data<>(service.create(jwt.getSubject(), request)));
    }

    @GetMapping("/playlists")
    @Operation(summary = "내 플레이리스트 목록", security = @SecurityRequirement(name = "bearerAuth"))
    public Data<List<PlaylistSummary>> mine(@AuthenticationPrincipal Jwt jwt) {
        return new Data<>(service.mine(jwt.getSubject()));
    }

    @GetMapping("/playlists/{playlistId}")
    @Operation(summary = "내 플레이리스트 상세", security = @SecurityRequirement(name = "bearerAuth"))
    public Data<PlaylistResponse> mine(@AuthenticationPrincipal Jwt jwt, @PathVariable Long playlistId) {
        return new Data<>(service.mine(jwt.getSubject(), playlistId));
    }

    @PutMapping("/playlists/{playlistId}/tracks")
    @Operation(summary = "내 플레이리스트 곡 목록 교체", security = @SecurityRequirement(name = "bearerAuth"))
    public Data<PlaylistResponse> replaceTracks(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long playlistId,
            @Valid @RequestBody ReplaceTracksRequest request) {
        return new Data<>(service.replaceTracks(jwt.getSubject(), playlistId, request.trackIds()));
    }

    @PostMapping("/playlists/{playlistId}/share")
    @Operation(summary = "공유 코드 발급", security = @SecurityRequirement(name = "bearerAuth"))
    public Data<ShareResponse> share(@AuthenticationPrincipal Jwt jwt, @PathVariable Long playlistId) {
        return new Data<>(service.share(jwt.getSubject(), playlistId));
    }

    @DeleteMapping("/playlists/{playlistId}/share")
    @Operation(summary = "공유 취소 및 현재 청취 기록 삭제", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> revokeShare(@AuthenticationPrincipal Jwt jwt, @PathVariable Long playlistId) {
        service.revokeShare(jwt.getSubject(), playlistId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/shared/playlists/{shareCode}")
    @Operation(summary = "공유 플레이리스트 조회")
    public Data<PlaylistResponse> shared(@PathVariable String shareCode) {
        return new Data<>(service.shared(shareCode));
    }

    @PutMapping("/shared/playlists/{shareCode}/listening")
    @Operation(summary = "재생 중임을 알리고 현재 위치 갱신", security = @SecurityRequirement(name = "bearerAuth"))
    public Data<HeartbeatResponse> heartbeat(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String shareCode,
            @Valid @RequestBody ListeningHeartbeatRequest request) {
        return new Data<>(service.heartbeat(jwt.getSubject(), shareCode, request));
    }

    @DeleteMapping("/shared/playlists/{shareCode}/listening")
    @Operation(summary = "재생 종료", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> stop(@AuthenticationPrincipal Jwt jwt, @PathVariable String shareCode) {
        service.stop(jwt.getSubject(), shareCode);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/shared/playlists/{shareCode}/active-listeners")
    @Operation(summary = "현재 내 H3 셀에서 이 플레이리스트를 듣는 인원", security = @SecurityRequirement(name = "bearerAuth"))
    public Data<ActiveListenersResponse> activeListeners(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String shareCode,
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam Instant recordedAt) {
        return new Data<>(service.activeListeners(jwt.getSubject(), shareCode, lat, lng, recordedAt));
    }
}
