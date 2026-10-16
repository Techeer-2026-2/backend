package com.techeer.backend.domain.playlist.controller;

import com.techeer.backend.domain.playlist.dto.ActiveListenersResponse;
import com.techeer.backend.domain.playlist.dto.HeartbeatResponse;
import com.techeer.backend.domain.playlist.dto.ListeningHeartbeatRequest;
import com.techeer.backend.domain.playlist.dto.PlaylistDetailResponse;
import com.techeer.backend.domain.playlist.dto.ShareLinkResponse;
import com.techeer.backend.domain.playlist.service.PlaylistShareService;
import com.techeer.backend.global.resolver.MemberId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 플레이리스트 공유와 현재 셀 청취 인원. 공유 조회(GET /shared/playlists/{shareToken})만 로그인 없이 열려 있다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Playlist Share", description = "플레이리스트 공유·현재 셀 청취 인원")
public class PlaylistShareController {

    private final PlaylistShareService shareService;

    @Operation(summary = "공유 링크 생성", description = "내 플레이리스트의 공유 토큰을 만든다. 이미 공유 중이면 같은 토큰을 돌려준다. 곡이 없으면 409.")
    @PostMapping("/playlists/{playlistId}/share-links")
    public ShareLinkResponse share(@MemberId Long memberId, @PathVariable Long playlistId) {
        return shareService.share(memberId, playlistId);
    }

    @Operation(summary = "공유 취소", description = "공유를 취소하고 청취 기록을 지운다. 이전 토큰으로는 더 이상 조회할 수 없다.")
    @DeleteMapping("/playlists/{playlistId}/share-links")
    public ResponseEntity<Void> revokeShare(@MemberId Long memberId, @PathVariable Long playlistId) {
        shareService.revokeShare(memberId, playlistId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "공유 플레이리스트 조회", description = "공유 토큰으로 곡 목록을 조회한다. 로그인이 필요 없다.")
    @GetMapping("/shared/playlists/{shareToken}")
    public PlaylistDetailResponse getShared(@PathVariable String shareToken) {
        return shareService.getShared(shareToken);
    }

    @Operation(summary = "재생 중 신호 전송",
            description = "재생 중 약 20초마다 현재 위치와 측정 시각을 보낸다. 마지막 신호가 60초 이내이고 위치가 30초 이내인 회원만 청취 인원에 센다.")
    @PutMapping("/shared/playlists/{shareToken}/listening")
    public HeartbeatResponse heartbeat(
            @MemberId Long memberId, @PathVariable String shareToken,
            @Valid @RequestBody ListeningHeartbeatRequest request) {
        return shareService.heartbeat(memberId, shareToken, request);
    }

    @Operation(summary = "재생 종료", description = "일시정지·종료할 때 호출한다. 이미 종료됐어도 204.")
    @DeleteMapping("/shared/playlists/{shareToken}/listening")
    public ResponseEntity<Void> stop(@MemberId Long memberId, @PathVariable String shareToken) {
        shareService.stop(memberId, shareToken);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "현재 셀 청취 인원",
            description = "조회자의 현재 위치(H3 셀, 해상도 8)에서 지금 이 플레이리스트를 듣는 고유 회원 수. 이름이나 개별 위치는 노출하지 않는다. 로그인 필요.")
    @GetMapping("/shared/playlists/{shareToken}/active-listeners")
    public ActiveListenersResponse activeListeners(
            @MemberId Long memberId, @PathVariable String shareToken,
            @RequestParam double lat, @RequestParam double lng,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant recordedAt) {
        return shareService.activeListeners(shareToken, lat, lng, recordedAt);
    }
}
