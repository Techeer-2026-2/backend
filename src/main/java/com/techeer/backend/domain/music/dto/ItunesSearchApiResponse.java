package com.techeer.backend.domain.music.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * iTunes Search API의 원본 응답 모양. 서비스 내부에서만 쓰고,
 * {@link TrackCandidateResponse}로 변환해서 내보낸다.
 */
public record ItunesSearchApiResponse(List<Result> results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            Long trackId,
            String trackName,
            String artistName,
            String collectionName,
            String artworkUrl100,
            String previewUrl,
            Integer trackTimeMillis,
            String primaryGenreName) {
    }
}
