package com.techeer.backend.domain.kakao.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techeer.backend.domain.kakao.service.KakaoPlaceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(KakaoPlaceController.class)
class KakaoPlaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KakaoPlaceService kakaoPlaceService;

    @Test
    @DisplayName("keyword 가 비어 있으면 카카오를 부르지 않고 400 INVALID_INPUT 을 돌려준다")
    void blankKeywordIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/places/search").param("keyword", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("keyword 가 50자를 넘으면 카카오를 부르지 않고 400 INVALID_INPUT 을 돌려준다")
    void tooLongKeywordIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/places/search").param("keyword", "가".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(kakaoPlaceService);
    }

    @Test
    @DisplayName("keyword 가 정확히 50자면 통과해서 카카오 검색을 호출한다")
    void keywordOfMaxLengthIsAccepted() throws Exception {
        mockMvc.perform(get("/api/v1/places/search").param("keyword", "가".repeat(50)))
                .andExpect(status().isOk());

        verify(kakaoPlaceService).search("가".repeat(50));
    }
}
