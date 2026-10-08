package com.techeer.backend.domain.playlist.controller;

import com.techeer.backend.domain.playlist.dto.PlaylistCreateRequest;
import com.techeer.backend.domain.playlist.dto.PlaylistDetailResponse;
import com.techeer.backend.domain.playlist.dto.PlaylistResponse;
import com.techeer.backend.domain.playlist.dto.PlaylistTrackAddRequest;
import com.techeer.backend.domain.playlist.service.PlaylistService;
import com.techeer.backend.global.resolver.MemberId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/playlists")
@RequiredArgsConstructor
@Tag(name = "Playlist", description = "플레이리스트")
public class PlaylistController {

    private final PlaylistService playlistService;

    @Operation(summary = "플레이리스트 생성")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public PlaylistResponse create(@MemberId Long memberId, @RequestBody PlaylistCreateRequest request) {
        return playlistService.create(memberId, request);
    }

    @Operation(summary = "플레이리스트 목록 조회")
    @GetMapping
    public List<PlaylistResponse> findAll(@MemberId Long memberId) {
        return playlistService.findAll(memberId);
    }

    @Operation(summary = "플레이리스트 상세 조회")
    @GetMapping("/{playlistId}")
    public PlaylistDetailResponse findDetail(@MemberId Long memberId, @PathVariable Long playlistId) {
        return playlistService.findDetail(memberId, playlistId);
    }

    @Operation(summary = "플레이리스트 삭제")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{playlistId}")
    public void delete(@MemberId Long memberId, @PathVariable Long playlistId) {
        playlistService.delete(memberId, playlistId);
    }

    @Operation(summary = "플레이리스트에 곡 추가")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/{playlistId}/tracks")
    public void addTracks(
            @MemberId Long memberId,
            @PathVariable Long playlistId,
            @RequestBody List<PlaylistTrackAddRequest> requests) {
        playlistService.addTracks(memberId, playlistId, requests);
    }

    @Operation(summary = "플레이리스트에서 곡 삭제")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{playlistId}/tracks/{trackId}")
    public void removeTrack(
            @MemberId Long memberId,
            @PathVariable Long playlistId,
            @PathVariable Long trackId) {
        playlistService.removeTrack(memberId, playlistId, trackId);
    }
}
