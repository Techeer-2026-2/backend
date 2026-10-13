package com.techeer.backend.domain.kakao.controller;

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
}
