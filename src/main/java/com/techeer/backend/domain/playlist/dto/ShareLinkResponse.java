package com.techeer.backend.domain.playlist.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "플레이리스트 공유 링크")
public record ShareLinkResponse(
        @Schema(description = "공유 토큰(UUID). 앱의 공유 URL 에 넣어 전달한다", example = "3f2b8c1e-6a0d-4e52-9d3a-1c7f0a9b4e11")
        String shareToken,
        @Schema(description = "이 토큰으로 공유 플레이리스트를 조회하는 API 경로", example = "/api/v1/shared/playlists/3f2b8c1e-6a0d-4e52-9d3a-1c7f0a9b4e11")
        String apiPath) {
}
