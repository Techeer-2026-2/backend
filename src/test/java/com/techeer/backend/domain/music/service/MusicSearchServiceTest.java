package com.techeer.backend.domain.music.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.techeer.backend.domain.music.dto.TrackCandidateResponse;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MusicSearchServiceTest {

    @Test
    void 검색_결과를_TrackCandidateResponse로_변환한다() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://itunes.apple.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestToUriTemplate(
                        "https://itunes.apple.com/search?term={term}&entity=song", "밤편지"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "results": [
                            {
                              "trackId": 1234,
                              "trackName": "밤편지",
                              "artistName": "아이유",
                              "collectionName": "밤편지",
                              "artworkUrl100": "https://example.com/art.jpg",
                              "previewUrl": "https://example.com/preview.m4a",
                              "trackTimeMillis": 240000,
                              "primaryGenreName": "K-Pop"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        MusicSearchService service = new MusicSearchService(builder.build());

        List<TrackCandidateResponse> result = service.search("밤편지");

        assertThat(result).containsExactly(new TrackCandidateResponse(
                1234L, "밤편지", "아이유", "밤편지",
                "https://example.com/art.jpg", "https://example.com/preview.m4a", 240000, "K-Pop"));
    }

    @Test
    void 검색_결과가_없으면_빈_목록을_반환한다() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://itunes.apple.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestToUriTemplate(
                        "https://itunes.apple.com/search?term={term}&entity=song", "zzzzz"))
                .andRespond(withSuccess("{ \"results\": [] }", MediaType.APPLICATION_JSON));

        MusicSearchService service = new MusicSearchService(builder.build());

        assertThat(service.search("zzzzz")).isEmpty();
    }

    @Test
    void iTunes_호출이_실패하면_EXTERNAL_API_ERROR_예외를_던진다() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://itunes.apple.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestToUriTemplate(
                        "https://itunes.apple.com/search?term={term}&entity=song", "밤편지"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        MusicSearchService service = new MusicSearchService(builder.build());

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> service.search("밤편지"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
        assertThat(exception.getCause()).isNotNull();
    }

    @Test
    void 응답_본문이_비어_있으면_EXTERNAL_API_ERROR_예외를_던진다() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://itunes.apple.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestToUriTemplate(
                        "https://itunes.apple.com/search?term={term}&entity=song", "밤편지"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        MusicSearchService service = new MusicSearchService(builder.build());

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> service.search("밤편지"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }
}
