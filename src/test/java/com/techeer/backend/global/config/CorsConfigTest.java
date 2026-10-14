package com.techeer.backend.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techeer.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 브라우저가 보내는 CORS 사전 요청(OPTIONS)과 실제 요청을 검증한다.
 * 허용 출처는 application.yml 의 cors.allowed-origins 기본값(localhost:5173)이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CorsConfigTest {

    private static final String URL = "/api/v1/playlists";
    private static final String ALLOWED = "http://localhost:5173";
    private static final String NOT_ALLOWED = "http://evil.example.com";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("허용된 출처의 사전 요청은 통과하고, 사용하는 헤더가 허용된다")
    void preflightFromAllowedOrigin() throws Exception {
        mockMvc.perform(options(URL)
                        .header(HttpHeaders.ORIGIN, ALLOWED)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-member-id,authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED))
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS));
    }

    @Test
    @DisplayName("허용되지 않은 출처의 사전 요청은 403 으로 거절한다")
    void preflightFromNotAllowedOrigin() throws Exception {
        mockMvc.perform(options(URL)
                        .header(HttpHeaders.ORIGIN, NOT_ALLOWED)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("허용되지 않은 헤더를 요구하는 사전 요청은 거절한다")
    void preflightWithNotAllowedHeader() throws Exception {
        mockMvc.perform(options(URL)
                        .header(HttpHeaders.ORIGIN, ALLOWED)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-secret"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("허용된 출처의 실제 요청 응답에는 허용 헤더가 붙는다")
    void actualRequestFromAllowedOrigin() throws Exception {
        mockMvc.perform(get(URL).header(HttpHeaders.ORIGIN, ALLOWED).header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED));
    }

    @Test
    @DisplayName("허용되지 않은 출처의 실제 요청은 거절한다")
    void actualRequestFromNotAllowedOrigin() throws Exception {
        mockMvc.perform(get(URL).header(HttpHeaders.ORIGIN, NOT_ALLOWED).header("X-Member-Id", "1"))
                .andExpect(status().isForbidden());
    }
}
