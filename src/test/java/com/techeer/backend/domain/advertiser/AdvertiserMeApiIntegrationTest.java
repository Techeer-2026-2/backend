package com.techeer.backend.domain.advertiser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.advertiser.entity.Advertiser;
import com.techeer.backend.domain.advertiser.entity.Plan;
import com.techeer.backend.domain.advertiser.repository.AdvertiserRepository;
import com.techeer.backend.domain.auth.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET·PATCH /api/v1/owners/me 를 실제 PostgreSQL(Testcontainers)에 붙여 검증한다.
 * 광고주 ID 는 요청 파라미터가 아니라 Authorization 헤더의 access token 에서 꺼낸다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AdvertiserMeApiIntegrationTest {

    private static final String URL = "/api/v1/owners/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdvertiserRepository advertiserRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 각 테스트가 저장한 광고주를 삭제해 다음 테스트를 격리한다.
     */
    @AfterEach
    void tearDown() {
        advertiserRepository.deleteAll();
    }

    /**
     * 조회 응답에 사업자명·이메일·요금제가 있고 비밀번호 관련 값은 없는지 검증한다.
     */
    @Test
    @DisplayName("내 정보를 조회하면 비밀번호 없이 사업자명·이메일·요금제가 온다")
    void getMe() throws Exception {
        Advertiser saved = saveAdvertiser("owner@test.com", "테커 카페");

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(saved)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(saved.getUserId()))
                .andExpect(jsonPath("$.email").value("owner@test.com"))
                .andExpect(jsonPath("$.businessName").value("테커 카페"))
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    /**
     * 토큰의 광고주 본인 정보만 오고, 요청에 다른 광고주 ID 를 끼워 보내도 영향이 없는지 검증한다.
     */
    @Test
    @DisplayName("토큰의 광고주 정보만 오고, 다른 광고주 ID 를 파라미터로 보내도 무시된다")
    void onlyOwnInformationIsReturned() throws Exception {
        Advertiser mine = saveAdvertiser("mine@test.com", "내 가게");
        Advertiser other = saveAdvertiser("other@test.com", "남의 가게");

        mockMvc.perform(get(URL)
                        .param("advertiserId", String.valueOf(other.getUserId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mine)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(mine.getUserId()))
                .andExpect(jsonPath("$.email").value("mine@test.com"));
    }

    /**
     * 토큰이 없으면 AUTH_REQUIRED, 올바르지 않으면 INVALID_TOKEN 으로 401 이 오는지(조회·수정 모두) 검증한다.
     */
    @Test
    @DisplayName("토큰이 없으면 401 AUTH_REQUIRED, 올바르지 않으면 401 INVALID_TOKEN")
    void requiresValidAccessToken() throws Exception {
        Advertiser saved = saveAdvertiser("owner@test.com", "테커 카페");
        String refreshToken = jwtTokenProvider.createRefreshToken(saved.getUserId());

        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Basic abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        // refresh token 은 access token 자리에 쓸 수 없다
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        mockMvc.perform(patch(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessName\": \"새 이름\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        assertThat(advertiserRepository.findById(saved.getUserId()).orElseThrow().getBusinessName())
                .isEqualTo("테커 카페");
    }

    /**
     * 토큰은 유효하지만 계정이 탈퇴(soft delete)된 경우 404 인지 검증한다.
     */
    @Test
    @DisplayName("토큰이 살아 있어도 탈퇴한 광고주는 404 ADVERTISER_NOT_FOUND")
    void deletedAdvertiserIsNotFound() throws Exception {
        Advertiser saved = saveAdvertiser("gone@test.com", "탈퇴한 가게");
        String token = bearer(saved);
        jdbcTemplate.update("UPDATE advertisers SET deleted_at = now() WHERE user_id = ?", saved.getUserId());

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADVERTISER_NOT_FOUND"));
        mockMvc.perform(patch(URL)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessName\": \"새 이름\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADVERTISER_NOT_FOUND"));
    }

    /**
     * 사업자명이 바뀌어 DB 에 반영되고(앞뒤 공백 제거), 이메일·요금제·해시는 그대로인지 검증한다.
     */
    @Test
    @DisplayName("사업자명을 수정하면 DB 에 반영되고 다른 값은 그대로다")
    void updateMe() throws Exception {
        Advertiser saved = saveAdvertiser("owner@test.com", "테커 카페");

        mockMvc.perform(patch(URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(saved))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessName\": \"  테커 로스터리  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("테커 로스터리"));

        Advertiser reloaded = advertiserRepository.findById(saved.getUserId()).orElseThrow();
        assertThat(reloaded.getBusinessName()).isEqualTo("테커 로스터리");
        assertThat(reloaded.getEmail()).isEqualTo("owner@test.com");
        assertThat(reloaded.getPlan()).isEqualTo(Plan.FREE);
        assertThat(reloaded.getPasswordHash()).isEqualTo("hash");
    }

    /**
     * 요청 본문에 email, plan 을 끼워 보내도 무시되고 사업자명만 바뀌는지 검증한다.
     */
    @Test
    @DisplayName("요청에 email·plan 을 같이 보내도 무시된다")
    void updateMeIgnoresOtherFields() throws Exception {
        Advertiser saved = saveAdvertiser("owner@test.com", "테커 카페");

        mockMvc.perform(patch(URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(saved))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessName\": \"새 이름\", \"email\": \"hacker@test.com\", \"plan\": \"PREMIUM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner@test.com"))
                .andExpect(jsonPath("$.plan").value("FREE"));

        Advertiser reloaded = advertiserRepository.findById(saved.getUserId()).orElseThrow();
        assertThat(reloaded.getBusinessName()).isEqualTo("새 이름");
        assertThat(reloaded.getEmail()).isEqualTo("owner@test.com");
        assertThat(reloaded.getPlan()).isEqualTo(Plan.FREE);
    }

    /**
     * 사업자명이 공백이면 400 이고 값이 바뀌지 않는지 검증한다.
     */
    @Test
    @DisplayName("사업자명이 공백이면 400")
    void updateMeRejectsBlank() throws Exception {
        Advertiser saved = saveAdvertiser("owner@test.com", "테커 카페");

        mockMvc.perform(patch(URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(saved))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessName\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(advertiserRepository.findById(saved.getUserId()).orElseThrow().getBusinessName())
                .isEqualTo("테커 카페");
    }

    private String bearer(Advertiser advertiser) {
        return "Bearer " + jwtTokenProvider.createAccessToken(advertiser.getUserId());
    }

    private Advertiser saveAdvertiser(String email, String businessName) {
        return advertiserRepository.save(Advertiser.create(email, "hash", businessName));
    }
}
