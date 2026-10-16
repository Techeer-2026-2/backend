package com.techeer.backend.domain.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.backend.TestcontainersConfiguration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code auth.legacy-member-header=false}(프론트 로그인 전환 후 운영 설정)이면 X-Member-Id 가 더 이상 통하지 않고
 * 로그인 토큰만 받는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "auth.legacy-member-header=false")
class LegacyMemberHeaderOffIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbcTemplate.update("delete from users");
    }

    @Test
    @DisplayName("임시 헤더를 끄면 X-Member-Id 만 보낸 요청은 401 AUTH_REQUIRED (내 정보와 기존 API 모두)")
    void headerOnlyRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("X-Member-Id", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        mockMvc.perform(get("/api/v1/playlists").header("X-Member-Id", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    @DisplayName("임시 헤더를 꺼도 로그인 토큰으로는 정상 사용한다")
    void tokenStillWorks() throws Exception {
        Map<String, String> account = Map.of("email", "listener@techeer.com", "password", "password1234");
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                Map.of("email", "listener@techeer.com", "password", "password1234", "nickname", "출근러"))))
                .andExpect(status().isCreated());
        String accessToken = objectMapper.readTree(
                        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsBytes(account)))
                                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("출근러"));
    }
}
