package com.techeer.backend.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.backend.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * /api/v1/members/* 를 실제 PostgreSQL(Testcontainers)에 붙여 검증한다.
 *
 * <p>중복 가입·refresh token 회전·동시 요청은 DB 의 unique 제약과 조건부 UPDATE 에 기대므로 실제 DB 로 확인해야 의미가 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MemberAuthApiIntegrationTest {

    private static final String SIGNUP = "/api/v1/members/signup";
    private static final String LOGIN = "/api/v1/members/login";
    private static final String REFRESH = "/api/v1/members/refresh";
    private static final String LOGOUT = "/api/v1/members/logout";
    private static final String EMAIL = "listener@techeer.com";
    private static final String PASSWORD = "password1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbcTemplate.update("delete from members");
        jdbcTemplate.update("delete from advertisers");
    }

    @Test
    @DisplayName("가입하면 201 과 회원 정보를 돌려주고, 비밀번호는 해시로만 저장하며 이메일은 소문자로 통일한다")
    void signupStoresHashedPasswordAndLowercaseEmail() throws Exception {
        mockMvc.perform(signup("Listener@Techeer.com", PASSWORD, "출근러"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").isNumber())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.nickname").value("출근러"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String stored = jdbcTemplate.queryForObject("select password_hash from members", String.class);
        assertThat(stored).isNotEqualTo(PASSWORD).startsWith("$2");
    }

    @Test
    @DisplayName("대소문자만 다른 같은 이메일로 다시 가입하면 409 EMAIL_DUPLICATED")
    void duplicateEmailIsRejected() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, "첫번째")).andExpect(status().isCreated());

        mockMvc.perform(signup("LISTENER@techeer.com", PASSWORD, "두번째"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_DUPLICATED"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from members", Long.class)).isEqualTo(1L);
    }

    @Test
    @DisplayName("잘못된 이메일, 짧은 비밀번호, 닉네임 없음, 72바이트를 넘는 한글 비밀번호는 모두 400 (500 이 아니다)")
    void invalidSignupIsRejected() throws Exception {
        mockMvc.perform(signup("not-an-email", PASSWORD, "닉")).andExpect(status().isBadRequest());
        mockMvc.perform(signup(EMAIL, "short", "닉")).andExpect(status().isBadRequest());
        mockMvc.perform(signup(EMAIL, PASSWORD, " ")).andExpect(status().isBadRequest());
        mockMvc.perform(signup(EMAIL, "가".repeat(25), "닉")).andExpect(status().isBadRequest());
        // 이모지는 UTF-16 으로는 2단위지만 한 글자다: 4개는 4글자라 거절, 8개는 8글자(32바이트)라 통과
        mockMvc.perform(signup(EMAIL, "😀".repeat(4), "닉")).andExpect(status().isBadRequest());
        mockMvc.perform(signup(EMAIL, "가".repeat(24), "닉")).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("같은 이메일로 동시에 가입해도 한 명만 만들어지고 나머지는 409 이다 (500 이 아니다)")
    void concurrentSignupCreatesOnlyOneMember() throws Exception {
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return mockMvc.perform(signup(EMAIL, PASSWORD, "동시")).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            int created = 0;
            int conflict = 0;
            for (Future<Integer> result : results) {
                int code = result.get(30, TimeUnit.SECONDS);
                if (code == 201) {
                    created++;
                } else if (code == 409) {
                    conflict++;
                }
            }
            assertThat(created).isEqualTo(1);
            assertThat(conflict).isEqualTo(threads - 1);
            assertThat(jdbcTemplate.queryForObject("select count(*) from members", Long.class)).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("로그인하면 access/refresh token 을 받는다")
    void loginIssuesTokens() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, "출근러")).andExpect(status().isCreated());

        mockMvc.perform(json(LOGIN, Map.of("email", "LISTENER@techeer.com", "password", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800));
    }

    @Test
    @DisplayName("없는 이메일과 틀린 비밀번호는 같은 401 INVALID_CREDENTIALS")
    void wrongCredentialsAreIndistinguishable() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, "출근러")).andExpect(status().isCreated());

        mockMvc.perform(json(LOGIN, Map.of("email", EMAIL, "password", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mockMvc.perform(json(LOGIN, Map.of("email", "nobody@techeer.com", "password", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("refresh 하면 새 토큰을 받고, 쓴 refresh token 은 다시 쓸 수 없다")
    void refreshRotatesToken() throws Exception {
        String first = signupAndLogin().get("refreshToken").asText();

        JsonNode second = body(mockMvc.perform(refresh(first)).andExpect(status().isOk()).andReturn());
        assertThat(second.get("refreshToken").asText()).isNotEqualTo(first);

        mockMvc.perform(refresh(first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mockMvc.perform(refresh(second.get("refreshToken").asText())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("다른 곳에서 다시 로그인하면 이전 refresh token 은 쓸 수 없다 (회원당 세션 1개)")
    void newLoginInvalidatesPreviousRefreshToken() throws Exception {
        String old = signupAndLogin().get("refreshToken").asText();
        mockMvc.perform(json(LOGIN, Map.of("email", EMAIL, "password", PASSWORD))).andExpect(status().isOk());

        mockMvc.perform(refresh(old)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("같은 refresh token 으로 동시에 여러 번 요청해도 한 번만 성공한다")
    void concurrentRefreshSucceedsOnlyOnce() throws Exception {
        String token = signupAndLogin().get("refreshToken").asText();
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return mockMvc.perform(refresh(token)).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            int ok = 0;
            for (Future<Integer> result : results) {
                if (result.get(30, TimeUnit.SECONDS) == 200) {
                    ok++;
                }
            }
            assertThat(ok).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("로그아웃하면 그 refresh token 으로 갱신할 수 없고, 다시 로그아웃해도 204")
    void logoutRevokesRefreshToken() throws Exception {
        String token = signupAndLogin().get("refreshToken").asText();

        mockMvc.perform(json(LOGOUT, Map.of("refreshToken", token))).andExpect(status().isNoContent());
        mockMvc.perform(refresh(token)).andExpect(status().isUnauthorized());
        mockMvc.perform(json(LOGOUT, Map.of("refreshToken", token))).andExpect(status().isNoContent());
        mockMvc.perform(json(LOGOUT, Map.of("refreshToken", "garbage"))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("광고주 토큰으로는 회원 refresh/logout 을 못 하고, 회원 토큰으로는 광고주 refresh 를 못 한다")
    void advertiserAndMemberTokensAreNotInterchangeable() throws Exception {
        mockMvc.perform(json("/api/v1/owners/signup",
                Map.of("email", "owner@techeer.com", "password", PASSWORD, "businessName", "카페")))
                .andExpect(status().isCreated());
        String advertiserRefresh = body(mockMvc.perform(json("/api/v1/owners/login",
                Map.of("email", "owner@techeer.com", "password", PASSWORD))).andReturn()).get("refreshToken").asText();
        String memberRefresh = signupAndLogin().get("refreshToken").asText();

        mockMvc.perform(refresh(advertiserRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mockMvc.perform(json(LOGOUT, Map.of("refreshToken", advertiserRefresh)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(json("/api/v1/owners/refresh", Map.of("refreshToken", memberRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    private JsonNode signupAndLogin() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, "출근러")).andExpect(status().isCreated());
        return body(mockMvc.perform(json(LOGIN, Map.of("email", EMAIL, "password", PASSWORD)))
                .andExpect(status().isOk()).andReturn());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signup(
            String email, String password, String nickname) throws Exception {
        return json(SIGNUP, Map.of("email", email, "password", password, "nickname", nickname));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder refresh(String token)
            throws Exception {
        return json(REFRESH, Map.of("refreshToken", token));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder json(
            String url, Object payload) throws Exception {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(payload));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
