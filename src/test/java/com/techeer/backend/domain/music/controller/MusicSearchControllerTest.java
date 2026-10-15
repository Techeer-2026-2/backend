package com.techeer.backend.domain.music.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techeer.backend.domain.music.service.MusicSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MusicSearchController.class)
class MusicSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MusicSearchService musicSearchService;

    @Test
    @DisplayName("term 이 비어 있으면 iTunes를 부르지 않고 400 INVALID_INPUT 을 돌려준다")
    void blankTermIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/music/search").param("term", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(musicSearchService);
    }

    @Test
    @DisplayName("term 이 50자를 넘으면 iTunes를 부르지 않고 400 INVALID_INPUT 을 돌려준다")
    void tooLongTermIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/music/search").param("term", "가".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(musicSearchService);
    }

    @Test
    @DisplayName("term 이 정확히 50자면 통과해서 음악 검색을 호출한다")
    void termOfMaxLengthIsAccepted() throws Exception {
        mockMvc.perform(get("/api/v1/music/search").param("term", "가".repeat(50)))
                .andExpect(status().isOk());

        verify(musicSearchService).search("가".repeat(50));
    }
}
