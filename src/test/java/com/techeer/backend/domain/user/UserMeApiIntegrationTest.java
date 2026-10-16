package com.techeer.backend.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.auth.jwt.JwtTokenProvider;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 회원 토큰으로 식별하는 /api/v1/me 와 X-Member-Id 병행 모드를 실제 PostgreSQL(Testcontainers)로 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class UserMeApiIntegrationTest {

    private static final String ME = "/api/v1/me";
    private static final String PLAYLISTS = "/api/v1/playlists";
    private static final String PASSWORD = "password1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbcTemplate.update("delete from users");
    }

    @Test
    @DisplayName("로그인한 회원 토큰으로 내 정보를 조회한다 (비밀번호·해시는 응답에 없다)")
    void getMeWithAccessToken() throws Exception {
        long userId = signup("listener@techeer.com", "출근러");
        String accessToken = loginAccessToken("listener@techeer.com");

        mockMvc.perform(get(ME).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.email").value("listener@techeer.com"))
                .andExpect(jsonPath("$.nickname").value("출근러"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.refreshTokenHash").doesNotExist());
    }

    @Test
    @DisplayName("닉네임을 수정하면 앞뒤 공백을 지워 저장하고 다음 조회에도 반영된다")
    void patchMeChangesNickname() throws Exception {
        signup("listener@techeer.com", "출근러");
        String accessToken = loginAccessToken("listener@techeer.com");

        mockMvc.perform(patch(ME).header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("nickname", "  퇴근러  "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("퇴근러"));

        assertThat(jdbcTemplate.queryForObject("select nickname from users", String.class)).isEqualTo("퇴근러");
        mockMvc.perform(get(ME).header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.nickname").value("퇴근러"));
    }

    @Test
    @DisplayName("닉네임이 비었거나 50자를 넘으면 400 이고 값은 바뀌지 않는다")
    void patchMeRejectsInvalidNickname() throws Exception {
        signup("listener@techeer.com", "출근러");
        String accessToken = loginAccessToken("listener@techeer.com");

        for (String invalid : new String[] {"", "   ", "가".repeat(51)}) {
            mockMvc.perform(patch(ME).header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsBytes(Map.of("nickname", invalid))))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbcTemplate.queryForObject("select nickname from users", String.class)).isEqualTo("출근러");
    }

    @Test
    @DisplayName("앞뒤 공백을 지운 값이 50자 이하이면 공백 때문에 거절하지 않는다")
    void patchMeAcceptsPaddedNicknameWithinLimit() throws Exception {
        signup("listener@techeer.com", "출근러");
        String accessToken = loginAccessToken("listener@techeer.com");
        String nickname = "가".repeat(49);

        mockMvc.perform(patch(ME).header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("nickname", "  " + nickname + "  "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(nickname));
        assertThat(jdbcTemplate.queryForObject("select nickname from users", String.class)).isEqualTo(nickname);
    }

    @Test
    @DisplayName("이메일·비밀번호는 이 요청으로 바꿀 수 없다 (보내도 무시된다)")
    void patchMeIgnoresOtherFields() throws Exception {
        signup("listener@techeer.com", "출근러");
        String accessToken = loginAccessToken("listener@techeer.com");
        String hashBefore = jdbcTemplate.queryForObject("select password_hash from users", String.class);

        mockMvc.perform(patch(ME).header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "nickname", "퇴근러", "email", "other@techeer.com", "password", "hacked-password-1"))))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("select email from users", String.class)).isEqualTo("listener@techeer.com");
        assertThat(jdbcTemplate.queryForObject("select password_hash from users", String.class)).isEqualTo(hashBefore);
    }

    @Test
    @DisplayName("토큰이 없고 X-Member-Id 만 있으면(병행 모드) 그 회원의 정보를 조회한다")
    void getMeWithLegacyHeader() throws Exception {
        long userId = signup("listener@techeer.com", "출근러");

        mockMvc.perform(get(ME).header("X-Member-Id", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId));
    }

    @Test
    @DisplayName("토큰과 X-Member-Id 가 다르면 토큰의 회원이 우선한다 (헤더로 다른 회원 행세 불가)")
    void tokenWinsOverHeader() throws Exception {
        long mine = signup("mine@techeer.com", "나");
        long other = signup("other@techeer.com", "남");
        String accessToken = loginAccessToken("mine@techeer.com");

        mockMvc.perform(get(ME)
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Member-Id", String.valueOf(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(mine))
                .andExpect(jsonPath("$.nickname").value("나"));
    }

    @Test
    @DisplayName("잘못된 토큰·광고주 토큰·refresh 토큰은 X-Member-Id 가 있어도 401 INVALID_TOKEN")
    void invalidTokensAreRejectedEvenWithHeader() throws Exception {
        long userId = signup("listener@techeer.com", "출근러");
        String advertiserToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createUserRefreshToken(userId);

        for (String token : new String[] {"garbage", advertiserToken, refreshToken}) {
            mockMvc.perform(get(ME)
                            .header("Authorization", "Bearer " + token)
                            .header("X-Member-Id", String.valueOf(userId)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }
    }

    @Test
    @DisplayName("탈퇴한 회원의 토큰으로 조회·수정하면 404 USER_NOT_FOUND")
    void withdrawnUserGets404() throws Exception {
        signup("listener@techeer.com", "출근러");
        String accessToken = loginAccessToken("listener@techeer.com");
        jdbcTemplate.update("update users set deleted_at = now()");

        mockMvc.perform(get(ME).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        mockMvc.perform(patch(ME).header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("nickname", "새이름"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("기존 음악 앱 API(플레이리스트 목록)도 회원 토큰으로 식별한다")
    void existingMemberApiAcceptsToken() throws Exception {
        signup("listener@techeer.com", "출근러");
        String accessToken = loginAccessToken("listener@techeer.com");

        mockMvc.perform(get(PLAYLISTS).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(PLAYLISTS).header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    private long signup(String email, String nickname) throws Exception {
        String json = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                Map.of("email", email, "password", PASSWORD, "nickname", nickname))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("userId").asLong();
    }

    private String loginAccessToken(String email) throws Exception {
        MockHttpServletRequestBuilder login = post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("email", email, "password", PASSWORD)));
        JsonNode body = objectMapper.readTree(
                mockMvc.perform(login).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }
}
