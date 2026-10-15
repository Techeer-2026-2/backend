package com.techeer.backend.domain.music.service;

import com.techeer.backend.domain.music.dto.ItunesSearchApiResponse;
import com.techeer.backend.domain.music.dto.TrackCandidateResponse;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
@RequiredArgsConstructor
public class MusicSearchService {

    private final RestClient itunesRestClient;

    public List<TrackCandidateResponse> search(String term) {
        try {
            // country 파라미터는 쓰지 않는다 — entity=song과 같이 쓰면 검색 결과가 0건으로 나오는 버그가 있다(#47).
            ItunesSearchApiResponse response = itunesRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("term", "{term}")
                            .queryParam("entity", "song")
                            .build(term))
                    .retrieve()
                    .body(ItunesSearchApiResponse.class);

            if (response == null || response.results() == null) {
                throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
            }

            return response.results().stream()
                    .map(this::toTrackCandidateResponse)
                    .toList();
        } catch (RestClientException e) {
            log.warn("iTunes 음악 검색 호출 실패", e);
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, e);
        }
    }

    private TrackCandidateResponse toTrackCandidateResponse(ItunesSearchApiResponse.Result result) {
        return new TrackCandidateResponse(
                result.trackId(),
                result.trackName(),
                result.artistName(),
                result.collectionName(),
                result.artworkUrl100(),
                result.previewUrl(),
                result.trackTimeMillis(),
                result.primaryGenreName());
    }
}
