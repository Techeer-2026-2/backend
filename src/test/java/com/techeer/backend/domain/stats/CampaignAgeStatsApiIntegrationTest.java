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
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET /api/v1/campaigns/{id}/stats/by-age 를 실제 PostgreSQL(Testcontainers)에 붙여 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CampaignAgeStatsApiIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM click_events");
        jdbcTemplate.update("DELETE FROM ad_impressions");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM click_events");
        jdbcTemplate.update("DELETE FROM ad_impressions");
        campaignRepository.deleteAll();
    }

    @Test
    @DisplayName("연령대별 노출·클릭·CTR 을 나눠 주고, 연령을 모르는 노출은 unknown 으로 나온다")
    void countsByAgeGroup() throws Exception {
        Campaign campaign = saveCampaign();
        LocalDateTime at = LocalDateTime.of(2026, 11, 10, 9, 0);
        String click20 = saveImpression(campaign, "20s", at);
        saveImpression(campaign, "20s", at);
        saveImpression(campaign, "30s", at);
        saveImpression(campaign, null, at);
        saveClick(campaign, click20, at);

        mockMvc.perform(get(url(campaign) + "?from=2026-11-10T00:00:00&to=2026-11-11T00:00:00")
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ageGroup").value("10s"))
                .andExpect(jsonPath("$[0].impressionCount").value(0))
                .andExpect(jsonPath("$[1].ageGroup").value("20s"))
                .andExpect(jsonPath("$[1].impressionCount").value(2))
                .andExpect(jsonPath("$[1].clickCount").value(1))
                .andExpect(jsonPath("$[1].ctr").value(50.0))
                .andExpect(jsonPath("$[2].impressionCount").value(1))
                .andExpect(jsonPath("$[2].clickCount").value(0))
                .andExpect(jsonPath("$[5].ageGroup").value("unknown"))
                .andExpect(jsonPath("$[5].impressionCount").value(1));
    }

    @Test
    @DisplayName("남의 캠페인은 403")
    void otherUsersCampaignIsForbidden() throws Exception {
        Campaign campaign = saveCampaign();

        mockMvc.perform(get(url(campaign))
                        .header(HttpHeaders.AUTHORIZATION, bearer(OTHER_USER_ID)))
                .andExpect(status().isForbidden());
    }

    private String url(Campaign campaign) {
        return "/api/v1/campaigns/" + campaign.getCampaignId() + "/stats/by-age";
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(userId);
    }

    private Campaign saveCampaign() {
        return campaignRepository.save(Campaign.builder()
                .userId(USER_ID)
                .title("연령 테스트 캠페인")
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

    private String saveImpression(Campaign campaign, String ageGroup, LocalDateTime shownAt) {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO ad_impressions (notification_id, campaign_id, target_user_id, shown_at, age_group,"
                        + " created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, campaign.getCampaignId(), 1L, shownAt, ageGroup, shownAt, shownAt);
        return id;
    }

    private void saveClick(Campaign campaign, String notificationId, LocalDateTime clickedAt) {
        jdbcTemplate.update(
                "INSERT INTO click_events (notification_id, campaign_id, target_user_id, clicked_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                notificationId, campaign.getCampaignId(), 1L, clickedAt, clickedAt, clickedAt);
    }
}
