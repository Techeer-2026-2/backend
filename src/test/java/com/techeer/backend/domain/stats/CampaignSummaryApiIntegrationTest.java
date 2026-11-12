package com.techeer.backend.domain.stats;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.auth.jwt.JwtTokenProvider;
import com.techeer.backend.domain.campaign.entity.Campaign;
import com.techeer.backend.domain.campaign.entity.CampaignStatus;
import com.techeer.backend.domain.campaign.repository.CampaignRepository;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
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
 * GET /api/v1/campaigns/{id}/summary 의 권한·키 없음 분기를 실제 PostgreSQL 에 붙여 검증한다.
 * 키는 비워서 외부 호출이 일어나지 않게 한다 (호출 자체는 ClaudeMessageClientTest 에서 가짜 서버로 검증).
 */
@SpringBootTest(properties = "external.claude.api-key=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CampaignSummaryApiIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @AfterEach
    void tearDown() {
        campaignRepository.deleteAll();
    }

    @Test
    @DisplayName("키가 없으면 503 (AI_NOT_CONFIGURED)")
    void missingKeyIsServiceUnavailable() throws Exception {
        Campaign campaign = saveCampaign();

        mockMvc.perform(get("/api/v1/campaigns/" + campaign.getCampaignId() + "/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_ID)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_NOT_CONFIGURED"));
    }

    @Test
    @DisplayName("남의 캠페인은 키를 확인하기 전에 403")
    void otherUsersCampaignIsForbidden() throws Exception {
        Campaign campaign = saveCampaign();

        mockMvc.perform(get("/api/v1/campaigns/" + campaign.getCampaignId() + "/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(OTHER_USER_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("없는 캠페인은 404")
    void unknownCampaignIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/campaigns/999999/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("토큰이 없으면 401")
    void withoutTokenIsUnauthorized() throws Exception {
        Campaign campaign = saveCampaign();

        mockMvc.perform(get("/api/v1/campaigns/" + campaign.getCampaignId() + "/summary"))
                .andExpect(status().isUnauthorized());
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(userId);
    }

    private Campaign saveCampaign() {
        return campaignRepository.save(Campaign.builder()
                .userId(USER_ID)
                .title("요약 테스트 캠페인")
                .body("본문")
                .imageUrl("https://example.com/banner.png")
                .linkUrl("https://example.com/landing")
                .ageGroups(Set.of("20s"))
                .genders(Set.of("all"))
                .timeStart(LocalDateTime.of(2026, 11, 1, 0, 0))
                .timeEnd(LocalDateTime.of(2026, 12, 31, 23, 59))
                .status(CampaignStatus.ACTIVE)
                .build());
    }
}
