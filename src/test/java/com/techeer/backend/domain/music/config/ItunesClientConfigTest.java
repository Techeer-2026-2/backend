package com.techeer.backend.domain.music.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.backend.domain.music.dto.TrackCandidateResponse;
import com.techeer.backend.domain.music.service.MusicSearchService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ItunesClientConfigTest {

    @Test
    void iTunes_가_text_javascript_로_보낸_JSON_도_읽는다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // 스프링 부트가 만드는 ObjectMapper 처럼 모르는 필드(resultCount 등)는 무시하게 맞춘다.
        ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        RestClient client = new ItunesClientConfig().itunesRestClient(builder, objectMapper, "https://itunes.apple.com");

        server.expect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"resultCount":1,"results":[{"trackId":7,"trackName":"비","artistName":"가수",
                        "trackTimeMillis":200000,"primaryGenreName":"K-Pop"}]}
                        """, new MediaType("text", "javascript", java.nio.charset.StandardCharsets.UTF_8)));

        List<TrackCandidateResponse> result = new MusicSearchService(client).search("비");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).itunesTrackId()).isEqualTo(7L);
        assertThat(result.get(0).genre()).isEqualTo("K-Pop");
    }
}
