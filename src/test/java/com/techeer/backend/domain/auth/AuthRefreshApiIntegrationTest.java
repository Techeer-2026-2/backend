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
import com.techeer.backend.domain.auth.dto.RefreshRequest;
import com.techeer.backend.domain.auth.jwt.JwtProperties;
import com.techeer.backend.domain.auth.jwt.JwtTokenProvider;
import com.techeer.backend.domain.auth.jwt.TokenHasher;
import com.techeer.backend.domain.auth.jwt.TokenType;
import com.techeer.backend.domain.auth.service.AuthService;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * POST /api/v1/owners/refresh 를 실제 PostgreSQL(Testcontainers)에 붙여 검증한다.
 *
 * <p>회전(쓴 토큰 폐기)과 동시 요청 처리는 DB 의 조건부 UPDATE 에 기대므로 실제 DB 로 확인해야 의미가 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthRefreshApiIntegrationTest {

    private static final String LOGIN_URL = "/api/v1/owners/login";
    private static final String REFRESH_URL = "/api/v1/owners/refresh";
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
    private JwtProperties jwtProperties;

    @Autowired
    private AuthService authService;

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
     * 갱신하면 새 토큰 한 쌍이 오고, DB 의 해시가 새 refresh token 의 해시로 바뀌는지 검증한다.
     */
    @Test
    @DisplayName("갱신하면 새 토큰이 발급되고 DB 의 해시가 새 refresh token 으로 교체된다")
    void refreshIssuesNewTokens() throws Exception {
        Advertiser saved = saveAdvertiser("owner@test.com");
        String oldRefresh = loginAndGetRefreshToken("owner@test.com");

        JsonNode tokens = refresh(oldRefresh, 200);

        assertThat(tokens.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(jwtTokenProvider.parseAdvertiserId(tokens.get("accessToken").asText(), TokenType.ACCESS))
                .isEqualTo(saved.getUserId());
        String newRefresh = tokens.get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(oldRefresh);
        assertThat(advertiserRepository.findById(saved.getUserId()).orElseThrow().getRefreshTokenHash())
                .isEqualTo(TokenHasher.sha256Hex(newRefresh));
    }

    /**
     * 한 번 쓴 refresh token 은 다시 쓸 수 없고, 새로 받은 refresh token 은 계속 쓸 수 있는지 검증한다(회전).
     */
    @Test
    @DisplayName("이미 쓴 refresh token 은 다시 쓸 수 없고 새로 받은 것은 쓸 수 있다")
    void usedRefreshTokenCannotBeReused() throws Exception {
        saveAdvertiser("owner@test.com");
        String first = loginAndGetRefreshToken("owner@test.com");

        String second = refresh(first, 200).get("refreshToken").asText();
        mockMvc.perform(refreshRequest(first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        String third = refresh(second, 200).get("refreshToken").asText();
        assertThat(third).isNotEqualTo(second);
    }

    /**
     * 다시 로그인하면 이전 로그인의 refresh token 은 쓸 수 없는지(세션 하나만 유지) 검증한다.
     */
    @Test
    @DisplayName("다시 로그인하면 이전 로그인의 refresh token 은 거절된다")
    void tokenFromEarlierLoginIsRejected() throws Exception {
        saveAdvertiser("owner@test.com");
        String fromFirstLogin = loginAndGetRefreshToken("owner@test.com");
        String fromSecondLogin = loginAndGetRefreshToken("owner@test.com");

        refresh(fromFirstLogin, 401);
        refresh(fromSecondLogin, 200);
    }

    /**
     * 서명이 맞아도 종류가 다르거나 이상한 문자열이면 401, 비어 있으면 400 인지 검증한다.
     */
    @Test
    @DisplayName("access token·이상한 문자열은 401, 비어 있으면 400")
    void rejectsWrongTokens() throws Exception {
        Advertiser saved = saveAdvertiser("owner@test.com");
        loginAndGetRefreshToken("owner@test.com");

        refresh(jwtTokenProvider.createAccessToken(saved.getUserId()), 401);
        refresh("not-a-jwt", 401);
        mockMvc.perform(refreshRequest(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    /**
     * 로그아웃 상태(해시 없음)와 서명은 맞지만 DB 에 저장된 적 없는 토큰이 거절되는지 검증한다.
     */
    @Test
    @DisplayName("저장된 해시가 없는(로그아웃 상태) 광고주의 토큰은 거절된다")
    void rejectsWhenNoStoredHash() throws Exception {
        Advertiser saved = saveAdvertiser("owner@test.com");
        String refreshToken = loginAndGetRefreshToken("owner@test.com");
        jdbcTemplate.update("UPDATE advertisers SET refresh_token_hash = NULL WHERE user_id = ?", saved.getUserId());

        refresh(refreshToken, 401);
        // 서명은 맞지만 한 번도 저장되지 않은 새 토큰도 마찬가지다.
        refresh(jwtTokenProvider.createRefreshToken(saved.getUserId()), 401);
    }

    /**
     * 탈퇴(soft delete)한 광고주의 refresh token 은 거절되는지 검증한다.
     */
    @Test
    @DisplayName("탈퇴한 광고주의 refresh token 은 거절된다")
    void rejectsDeletedAdvertiser() throws Exception {
        Advertiser saved = saveAdvertiser("owner@test.com");
        String refreshToken = loginAndGetRefreshToken("owner@test.com");
        jdbcTemplate.update("UPDATE advertisers SET deleted_at = now() WHERE user_id = ?", saved.getUserId());

        refresh(refreshToken, 401);
    }

    /**
     * 유효 기간(14일)이 지난 refresh token 은 DB 해시가 일치해도 거절되는지 검증한다.
     */
    @Test
    @DisplayName("만료된 refresh token 은 거절된다")
    void rejectsExpiredToken() throws Exception {
        Advertiser saved = saveAdvertiser("owner@test.com");
        JwtTokenProvider fifteenDaysAgo = new JwtTokenProvider(
                jwtProperties, Clock.fixed(Instant.now().minus(Duration.ofDays(15)), java.time.ZoneOffset.UTC));
        String expired = fifteenDaysAgo.createRefreshToken(saved.getUserId());
        jdbcTemplate.update("UPDATE advertisers SET refresh_token_hash = ? WHERE user_id = ?",
                TokenHasher.sha256Hex(expired), saved.getUserId());

        refresh(expired, 401);
    }

    /**
     * 같은 refresh token 으로 동시에 여러 번 요청해도 정확히 한 번만 성공하는지 검증한다.
     */
    @Test
    @DisplayName("같은 refresh token 으로 동시에 요청하면 한 번만 성공한다")
    void concurrentRefreshSucceedsOnlyOnce() throws Exception {
        Advertiser saved = saveAdvertiser("owner@test.com");
        String refreshToken = loginAndGetRefreshToken("owner@test.com");

        int threads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Boolean> task = () -> {
                start.await();
                try {
                    authService.refresh(new RefreshRequest(refreshToken));
                    return true;
                } catch (BusinessException e) {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
                    return false;
                }
            };
            results.add(executor.submit(task));
        }
        start.countDown();

        int succeeded = 0;
        for (Future<Boolean> result : results) {
            if (result.get(30, TimeUnit.SECONDS)) {
                succeeded++;
            }
        }
        executor.shutdown();

        assertThat(succeeded).isEqualTo(1);
        assertThat(advertiserRepository.findById(saved.getUserId()).orElseThrow().getRefreshTokenHash())
                .isNotEqualTo(TokenHasher.sha256Hex(refreshToken));
    }

    private JsonNode refresh(String refreshToken, int expectedStatus) throws Exception {
        String body = mockMvc.perform(refreshRequest(refreshToken))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder refreshRequest(
            String refreshToken) {
        return post(REFRESH_URL)
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
