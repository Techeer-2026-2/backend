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
 * GET /api/v1/campaigns/{id}/stats/timeseries 를 실제 PostgreSQL(Testcontainers)에 붙여 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CampaignTimeseriesApiIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final String BASE = "/api/v1/campaigns/";

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
    @DisplayName("일 단위 추이: 날짜별 노출·클릭·CTR 을 돌려주고 기록 없는 날은 0 이다")
    void dailyTimeseries() throws Exception {
        Campaign campaign = saveCampaign();
        saveImpression(campaign, LocalDateTime.of(2026, 11, 10, 9, 0));
        saveImpression(campaign, LocalDateTime.of(2026, 11, 10, 18, 0));
        saveImpression(campaign, LocalDateTime.of(2026, 11, 10, 23, 30));
        saveClick(campaign, LocalDateTime.of(2026, 11, 10, 9, 5));
        saveImpression(campaign, LocalDateTime.of(2026, 11, 11, 10, 0));

        mockMvc.perform(get(url(campaign) + "?from=2026-11-10T00:00:00&to=2026-11-13T00:00:00&granularity=day")
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].bucketStart").value("2026-11-10T00:00:00"))
                .andExpect(jsonPath("$[0].impressionCount").value(3))
                .andExpect(jsonPath("$[0].clickCount").value(1))
                .andExpect(jsonPath("$[0].ctr").value(33.33))
                .andExpect(jsonPath("$[1].impressionCount").value(1))
                .andExpect(jsonPath("$[1].clickCount").value(0))
                .andExpect(jsonPath("$[1].ctr").value(0.0))
                .andExpect(jsonPath("$[2].bucketStart").value("2026-11-12T00:00:00"))
                .andExpect(jsonPath("$[2].impressionCount").value(0));
    }

    @Test
    @DisplayName("시간 단위 추이: 기록이 없는 시간은 0으로 채운다")
    void hourlyTimeseriesFillsGaps() throws Exception {
        Campaign campaign = saveCampaign();
        saveImpression(campaign, LocalDateTime.of(2026, 11, 10, 10, 30));
        saveImpression(campaign, LocalDateTime.of(2026, 11, 10, 12, 15));

        mockMvc.perform(get(url(campaign) + "?from=2026-11-10T10:00:00&to=2026-11-10T13:00:00&granularity=hour")
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].impressionCount").value(1))
                .andExpect(jsonPath("$[1].bucketStart").value("2026-11-10T11:00:00"))
                .andExpect(jsonPath("$[1].impressionCount").value(0))
                .andExpect(jsonPath("$[2].impressionCount").value(1));
    }

    @Test
    @DisplayName("단위를 생략하면 day 로 조회한다")
    void defaultGranularityIsDay() throws Exception {
        Campaign campaign = saveCampaign();
        saveImpression(campaign, LocalDateTime.of(2026, 11, 10, 9, 0));

        mockMvc.perform(get(url(campaign) + "?from=2026-11-10T00:00:00&to=2026-11-11T00:00:00")
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].impressionCount").value(1));
    }

    @Test
    @DisplayName("잘못된 단위는 400")
    void invalidGranularityIsBadRequest() throws Exception {
        Campaign campaign = saveCampaign();

        mockMvc.perform(get(url(campaign) + "?granularity=week")
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("시작이 끝보다 뒤면 400")
    void reversedRangeIsBadRequest() throws Exception {
        Campaign campaign = saveCampaign();

        mockMvc.perform(get(url(campaign) + "?from=2026-11-12T00:00:00&to=2026-11-10T00:00:00")
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("남의 캠페인은 403")
    void otherUsersCampaignIsForbidden() throws Exception {
        Campaign campaign = saveCampaign();

        mockMvc.perform(get(url(campaign) + "?granularity=day")
                        .header(HttpHeaders.AUTHORIZATION, bearer(OTHER_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("없는 캠페인은 404")
    void unknownCampaignIsNotFound() throws Exception {
        mockMvc.perform(get(BASE + "999999/stats/timeseries")
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_NOT_FOUND"));
    }

    @Test
    @DisplayName("토큰이 없으면 401")
    void withoutTokenIsUnauthorized() throws Exception {
        Campaign campaign = saveCampaign();

        mockMvc.perform(get(url(campaign) + "?granularity=day"))
                .andExpect(status().isUnauthorized());
    }

    private String url(Campaign campaign) {
        return BASE + campaign.getCampaignId() + "/stats/timeseries";
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(userId);
    }

    private Campaign saveCampaign() {
        return campaignRepository.save(Campaign.builder()
                .userId(USER_ID)
                .title("추이 테스트 캠페인")
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
