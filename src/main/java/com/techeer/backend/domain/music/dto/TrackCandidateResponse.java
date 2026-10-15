package com.techeer.backend.domain.music.dto;

/**
 * 음악 검색 결과 한 건. 플레이리스트 곡 추가 요청(PlaylistTrackAddRequest)과 필드 이름을 맞춰
 * 프론트가 검색 결과를 그대로 곡 추가 요청에 옮겨 쓸 수 있게 한다.
 */
public record TrackCandidateResponse(
        Long itunesTrackId,
        String trackName,
        String artistName,
        String collectionName,
        String artworkUrl,
        String previewUrl,
        Integer trackTimeMillis,
        String genre) {
}
