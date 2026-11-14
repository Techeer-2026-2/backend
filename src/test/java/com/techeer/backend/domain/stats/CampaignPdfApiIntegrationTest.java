package com.techeer.backend.domain.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.advertiser.entity.Advertiser;
import com.techeer.backend.domain.advertiser.repository.AdvertiserRepository;
import com.techeer.backend.domain.auth.jwt.JwtTokenProvider;
import com.techeer.backend.domain.campaign.entity.Campaign;
import com.techeer.backend.domain.campaign.entity.CampaignStatus;
import com.techeer.backend.domain.campaign.repository.CampaignRepository;
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * GET /api/v1/campaigns/{id}/report/pdf 를 실제 PostgreSQL(Testcontainers)에 붙여 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CampaignPdfApiIntegrationTest {

    private Long USER_ID;
    private Long OTHER_USER_ID;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private AdvertiserRepository advertiserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        USER_ID = advertiserRepository.save(Advertiser.create("owner@test.com", "hash", "테커 카페")).getUserId();
        OTHER_USER_ID = advertiserRepository.save(Advertiser.create("other@test.com", "hash", "다른 가게")).getUserId();
        jdbcTemplate.update("DELETE FROM click_events");
        jdbcTemplate.update("DELETE FROM ad_impressions");
    }

    @AfterEach
    void tearDown() {
        advertiserRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM click_events");
        jdbcTemplate.update("DELETE FROM ad_impressions");
        campaignRepository.deleteAll();
    }

    @Test
    @DisplayName("PDF 를 attachment 로 내려준다 (application/pdf, %PDF 시그니처)")
    void downloadsPdf() throws Exception {
        Campaign campaign = saveCampaign();
        saveImpression(campaign, LocalDateTime.of(2026, 11, 10, 9, 0));
        saveClick(campaign, LocalDateTime.of(2026, 11, 10, 9, 5));

        MvcResult result = mockMvc.perform(get("/api/v1/campaigns/" + campaign.getCampaignId() + "/report/pdf"
                        + "?from=2026-11-10T00:00:00&to=2026-11-12T00:00:00")
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        org.hamcrest.Matchers.containsString("application/pdf")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("campaign-" + campaign.getCampaignId() + "-report.pdf")))
                .andReturn();

        byte[] pdf = result.getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("남의 캠페인은 403")
    void otherUsersCampaignIsForbidden() throws Exception {
        Campaign campaign = saveCampaign();

        mockMvc.perform(get("/api/v1/campaigns/" + campaign.getCampaignId() + "/report/pdf")
                        .header(HttpHeaders.AUTHORIZATION, bearer(OTHER_USER_ID)))
                .andExpect(status().isForbidden());
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(userId);
    }

    private Campaign saveCampaign() {
        return campaignRepository.save(Campaign.builder()
                .userId(USER_ID)
                .title("리포트 PDF 테스트 캠페인")
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

    private void saveImpression(Campaign campaign, LocalDateTime shownAt) {
        jdbcTemplate.update(
                "INSERT INTO ad_impressions (notification_id, campaign_id, target_user_id, shown_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), campaign.getCampaignId(), 1L, shownAt, shownAt, shownAt);
    }

    private void saveClick(Campaign campaign, LocalDateTime clickedAt) {
        jdbcTemplate.update(
                "INSERT INTO click_events (notification_id, campaign_id, target_user_id, clicked_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), campaign.getCampaignId(), 1L, clickedAt, clickedAt, clickedAt);
    }
}
