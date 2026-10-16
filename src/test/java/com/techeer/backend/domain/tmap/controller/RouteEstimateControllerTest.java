package com.techeer.backend.domain.tmap.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techeer.backend.domain.tmap.service.TmapDurationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RouteEstimateController.class)
class RouteEstimateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TmapDurationService tmapDurationService;

    @Test
    @DisplayName("정상 요청이면 TmapDurationService를 호출해 소요시간을 돌려준다")
    void validRequestReturnsDuration() throws Exception {
        when(tmapDurationService.getDurationMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(25);

        mockMvc.perform(post("/api/v1/routes/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startLatitude": 37.5, "startLongitude": 127.0,
                                  "endLatitude": 37.6, "endLongitude": 127.1,
                                  "transportMode": "CAR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMinutes").value(25));

        verify(tmapDurationService).getDurationMinutes(127.0, 37.5, 127.1, 37.6,
                com.techeer.backend.domain.tmap.TransportMode.CAR);
    }

    @Test
    @DisplayName("필수 필드가 없으면 TMAP을 부르지 않고 400 INVALID_INPUT을 돌려준다")
    void missingFieldIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/routes/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startLatitude": 37.5, "startLongitude": 127.0,
                                  "endLatitude": 37.6, "endLongitude": 127.1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(tmapDurationService);
    }

    @Test
    @DisplayName("위도가 범위를 벗어나면 400 INVALID_INPUT을 돌려준다")
    void outOfRangeLatitudeIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/routes/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startLatitude": 137.5, "startLongitude": 127.0,
                                  "endLatitude": 37.6, "endLongitude": 127.1,
                                  "transportMode": "CAR"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(tmapDurationService);
    }
}
