package com.techeer.backend.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.advertiser.entity.Advertiser;
import com.techeer.backend.domain.advertiser.repository.AdvertiserRepository;
import com.techeer.backend.domain.auth.jwt.JwtTokenProvider;
import com.techeer.backend.domain.auth.jwt.TokenHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * POST /api/v1/owners/logout 을 실제 PostgreSQL(Testcontainers)에 붙여 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthLogoutApiIntegrationTest {

    private static final String LOGIN_URL = "/api/v1/owners/login";
    private static final String REFRESH_URL = "/api/v1/owners/refresh";
    private static final String LOGOUT_URL = "/api/v1/owners/logout";
    private static final String PASSWORD = "password1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdvertiserRepository advertiserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 각 테스트가 저장한 광고주를 삭제해 다음 테스트를 격리한다.
     */
    @AfterEach
    void tearDown() {
        advertiserRepository.deleteAll();
    }

    /**
     * 로그아웃하면 204(본문 없음)이고 DB 의 해시가 지워지며, 그 토큰으로는 더 이상 갱신할 수 없는지 검증한다.
     */
    @Test
    @DisplayName("로그아웃하면 204, 해시가 지워지고 그 토큰으로는 갱신할 수 없다")
    void logoutRevokesRefreshToken() throws Exception {
        Advertiser saved = saveAdvertiser("owner@test.com");
        String refreshToken = loginAndGetRefreshToken("owner@test.com");

        mockMvc.perform(tokenRequest(LOGOUT_URL, refreshToken))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(advertiserRepository.findById(saved.getUserId()).orElseThrow().getRefreshTokenHash()).isNull();
        mockMvc.perform(tokenRequest(REFRESH_URL, refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    /**
     * 이미 로그아웃한 토큰으로 다시 요청해도 같은 204 인지(여러 번 해도 결과가 같은지) 검증한다.
     */
    @Test
    @DisplayName("이미 로그아웃한 토큰으로 다시 로그아웃해도 204")
    void logoutIsIdempotent() throws Exception {
        saveAdvertiser("owner@test.com");
        String refreshToken = loginAndGetRefreshToken("owner@test.com");

        mockMvc.perform(tokenRequest(LOGOUT_URL, refreshToken)).andExpect(status().isNoContent());
        mockMvc.perform(tokenRequest(LOGOUT_URL, refreshToken)).andExpect(status().isNoContent());
    }

    /**
     * 갱신으로 이미 교체된 옛 토큰으로 로그아웃을 요청해도 지금 쓰는 새 세션은 유지되는지 검증한다.
     */
    @Test
    @DisplayName("교체된 옛 토큰으로 로그아웃해도 지금 쓰는 새 토큰은 계속 유효하다")
    void oldTokenDoesNotKillCurrentSession() throws Exception {
        saveAdvertiser("owner@test.com");
        String oldToken = loginAndGetRefreshToken("owner@test.com");
        String currentToken = objectMapper.readTree(mockMvc.perform(tokenRequest(REFRESH_URL, oldToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("refreshToken").asText();

        mockMvc.perform(tokenRequest(LOGOUT_URL, oldToken)).andExpect(status().isNoContent());

        mockMvc.perform(tokenRequest(REFRESH_URL, currentToken)).andExpect(status().isOk());
    }

    /**
     * 한 광고주의 로그아웃이 다른 광고주의 세션에는 영향을 주지 않는지 검증한다.
     */
    @Test
    @DisplayName("한 광고주가 로그아웃해도 다른 광고주의 세션은 그대로다")
    void logoutDoesNotAffectOthers() throws Exception {
        saveAdvertiser("a@test.com");
        Advertiser other = saveAdvertiser("b@test.com");
        String tokenA = loginAndGetRefreshToken("a@test.com");
        String tokenB = loginAndGetRefreshToken("b@test.com");

        mockMvc.perform(tokenRequest(LOGOUT_URL, tokenA)).andExpect(status().isNoContent());

        assertThat(advertiserRepository.findById(other.getUserId()).orElseThrow().getRefreshTokenHash())
                .isEqualTo(TokenHasher.sha256Hex(tokenB));
        mockMvc.perform(tokenRequest(REFRESH_URL, tokenB)).andExpect(status().isOk());
    }

    /**
     * 로그아웃한 뒤 다시 로그인하면 새 세션이 정상적으로 시작되는지 검증한다.
     */
    @Test
    @DisplayName("로그아웃 후 다시 로그인할 수 있다")
    void canLoginAgainAfterLogout() throws Exception {
        saveAdvertiser("owner@test.com");
        String first = loginAndGetRefreshToken("owner@test.com");
        mockMvc.perform(tokenRequest(LOGOUT_URL, first)).andExpect(status().isNoContent());

        String second = loginAndGetRefreshToken("owner@test.com");

        mockMvc.perform(tokenRequest(REFRESH_URL, second)).andExpect(status().isOk());
    }

    /**
     * access token·이상한 문자열은 401, 비어 있으면 400 인지 검증한다.
     */
    @Test
    @DisplayName("access token·이상한 문자열은 401, 비어 있으면 400")
    void rejectsWrongTokens() throws Exception {
        Advertiser saved = saveAdvertiser("owner@test.com");

        mockMvc.perform(tokenRequest(LOGOUT_URL, jwtTokenProvider.createAccessToken(saved.getUserId())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mockMvc.perform(tokenRequest(LOGOUT_URL, "not-a-jwt")).andExpect(status().isUnauthorized());
        mockMvc.perform(tokenRequest(LOGOUT_URL, ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private MockHttpServletRequestBuilder tokenRequest(String url, String refreshToken) {
        return post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"%s\"}".formatted(refreshToken));
    }

    private String loginAndGetRefreshToken(String email) throws Exception {
        String body = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("refreshToken").asText();
    }

    private Advertiser saveAdvertiser(String email) {
        return advertiserRepository.save(Advertiser.create(email, passwordEncoder.encode(PASSWORD), "테커 카페"));
    }
}
