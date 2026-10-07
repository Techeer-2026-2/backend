package com.techeer.backend.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.advertiser.entity.Advertiser;
import com.techeer.backend.domain.advertiser.repository.AdvertiserRepository;
import com.techeer.backend.domain.auth.jwt.JwtTokenProvider;
import com.techeer.backend.domain.auth.jwt.TokenHasher;
import com.techeer.backend.domain.auth.jwt.TokenType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * POST /api/v1/owners/login 을 실제 PostgreSQL(Testcontainers)에 붙여 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthLoginApiIntegrationTest {

    private static final String URL = "/api/v1/owners/login";
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
    private JdbcTemplate jdbcTemplate;

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
     * 로그인하면 두 토큰이 오고, 각 토큰에서 같은 광고주 ID 를 꺼낼 수 있으며,
     * DB 에는 refresh token 원문이 아니라 SHA-256 해시가 저장되는지 검증한다.
     */
    @Test
    @DisplayName("로그인하면 토큰이 발급되고 DB 에는 refresh token 의 해시만 저장된다")
    void loginSucceeds() throws Exception {
        Advertiser saved = saveAdvertiser("owner@test.com");

        String body = mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("owner@test.com", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(body);
        String accessToken = node.get("accessToken").asText();
        String refreshToken = node.get("refreshToken").asText();

        assertThat(jwtTokenProvider.parseAdvertiserId(accessToken, TokenType.ACCESS)).isEqualTo(saved.getUserId());
        assertThat(jwtTokenProvider.parseAdvertiserId(refreshToken, TokenType.REFRESH)).isEqualTo(saved.getUserId());

        String storedHash = advertiserRepository.findById(saved.getUserId()).orElseThrow().getRefreshTokenHash();
        assertThat(storedHash).isEqualTo(TokenHasher.sha256Hex(refreshToken)).isNotEqualTo(refreshToken);
    }

    /**
     * 이메일 대소문자는 구분하지 않고(가입 때 소문자로 저장하므로) 로그인되는지 검증한다.
     */
    @Test
    @DisplayName("이메일 대소문자가 달라도 로그인된다")
    void loginIgnoresEmailCase() throws Exception {
        saveAdvertiser("owner@test.com");

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("OWNER@Test.com", PASSWORD)))
                .andExpect(status().isOk());
    }

    /**
     * 비밀번호가 틀린 경우와 없는 이메일이 응답 본문까지 똑같아서 가입 여부를 구분할 수 없는지 검증한다.
     */
    @Test
    @DisplayName("틀린 비밀번호와 없는 이메일은 똑같은 401 로 응답한다")
    void wrongPasswordAndUnknownEmailLookTheSame() throws Exception {
        saveAdvertiser("owner@test.com");

        String wrongPassword = mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("owner@test.com", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("nobody@test.com", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownEmail).isEqualTo(wrongPassword);
    }

    /**
     * 탈퇴(soft delete)한 계정은 비밀번호가 맞아도 로그인되지 않는지 검증한다.
     */
    @Test
    @DisplayName("탈퇴한 계정은 비밀번호가 맞아도 401")
    void deletedAccountCannotLogin() throws Exception {
        Advertiser saved = saveAdvertiser("gone@test.com");
        jdbcTemplate.update("UPDATE advertisers SET deleted_at = now() WHERE user_id = ?", saved.getUserId());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("gone@test.com", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    /**
     * 다시 로그인하면 저장된 refresh token 해시가 새 값으로 바뀌는지(세션 하나만 유지) 검증한다.
     */
    @Test
    @DisplayName("다시 로그인하면 이전 refresh token 해시는 새 값으로 교체된다")
    void secondLoginReplacesRefreshTokenHash() throws Exception {
        Advertiser saved = saveAdvertiser("owner@test.com");

        String first = loginAndGetRefreshToken("owner@test.com");
        String second = loginAndGetRefreshToken("owner@test.com");

        assertThat(first).isNotEqualTo(second);
        assertThat(advertiserRepository.findById(saved.getUserId()).orElseThrow().getRefreshTokenHash())
                .isEqualTo(TokenHasher.sha256Hex(second));
    }

    /**
     * 이메일이나 비밀번호가 비어 있으면 400 인지 검증한다.
     */
    @Test
    @DisplayName("이메일이나 비밀번호가 비어 있으면 400")
    void blankFieldsAreRejected() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("", PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("owner@test.com", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private String loginAndGetRefreshToken(String email) throws Exception {
        String body = mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("refreshToken").asText();
    }

    private Advertiser saveAdvertiser(String email) {
        return advertiserRepository.save(Advertiser.create(email, passwordEncoder.encode(PASSWORD), "테커 카페"));
    }

    private String json(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }
}
