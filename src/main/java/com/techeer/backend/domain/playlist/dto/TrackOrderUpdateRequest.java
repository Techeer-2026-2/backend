package com.techeer.backend.domain.playlist.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 플레이리스트 안의 곡 전체 순서. 배열 순서가 곧 원하는 재생 순서다.
 */
public record TrackOrderUpdateRequest(@NotEmpty List<Long> playlistTrackIds) {
}
