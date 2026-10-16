package com.techeer.backend.domain.playlist.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techeer.backend.domain.playlist.service.PlaylistService;
import com.techeer.backend.domain.recommendation.RecommendationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlaylistController.class)
class PlaylistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaylistService playlistService;

    @MockitoBean
    private RecommendationService recommendationService;

    @Test
    @DisplayName("곡을 101개 보내면 플레이리스트 서비스를 부르지 않고 400 INVALID_INPUT 을 돌려준다")
    void tooManyTracksIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/playlists/1/tracks")
                        .header("X-Member-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trackListJson(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(playlistService);
    }

    @Test
    @DisplayName("곡이 정확히 100개면 통과해서 곡 추가를 호출한다")
    void exactlyMaxTracksIsAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/playlists/1/tracks")
                        .header("X-Member-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trackListJson(100)))
                .andExpect(status().isNoContent());

        verify(playlistService).addTracks(anyLong(), anyLong(), any());
    }

    private String trackListJson(int count) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append("""
                    {"itunesTrackId": %d, "trackName": "곡%d", "artistName": "아티스트"}
                    """.formatted(i, i));
        }
        return json.append("]").toString();
    }
}
