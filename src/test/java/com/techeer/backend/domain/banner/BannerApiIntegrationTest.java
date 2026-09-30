package com.techeer.backend.domain.banner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.campaign.entity.Campaign;
import com.techeer.backend.domain.campaign.entity.CampaignStatus;
import com.techeer.backend.domain.campaign.repository.CampaignRepository;
import com.techeer.backend.domain.click.repository.ClickEventRepository;
import com.techeer.backend.domain.impression.entity.AdImpression;
import com.techeer.backend.domain.impression.repository.AdImpressionRepository;
import com.techeer.backend.domain.stats.entity.CampaignStats;
import com.techeer.backend.domain.stats.repository.CampaignStatsRepository;
import com.techeer.backend.domain.targetuser.entity.TargetUser;
import com.techeer.backend.domain.targetuser.repository.TargetUserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * GET /api/v1/banners/match 를 실제 PostgreSQL(Testcontainers)에 붙여 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class BannerApiIntegrationTest {

    private static final String AGE_20S = "20s";
    private static final String AGE_30S = "30s";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TargetUserRepository targetUserRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private AdImpressionRepository adImpressionRepository;

    @Autowired
    private CampaignStatsRepository campaignStatsRepository;

    @Autowired
    private ClickEventRepository clickEventRepository;

    @AfterEach
    void tearDown() {
        clickEventRepository.deleteAll();
        campaignStatsRepository.deleteAll();
        adImpressionRepository.deleteAll();
        campaignRepository.deleteAll();
        targetUserRepository.deleteAll();
    }

    @Test
    @DisplayName("연령대가 맞는 진행 중 캠페인을 배너로 돌려주고 노출을 기록한다")
    void matchesActiveCampaignByAgeGroup() throws Exception {
        TargetUser user = saveUser(AGE_20S);
        Campaign campaign = saveCampaign(AGE_20S, CampaignStatus.ACTIVE, daysFromNow(-1), daysFromNow(1));

        String responseBody = mockMvc.perform(matchRequest(user.getTargetUserId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId").value(campaign.getCampaignId()))
                .andExpect(jsonPath("$.title").value(campaign.getTitle()))
                .andExpect(jsonPath("$.linkUrl").value(campaign.getLinkUrl()))
                .andReturn().getResponse().getContentAsString();

        String notificationId = JsonPath.read(responseBody, "$.notificationId");
        AdImpression impression = adImpressionRepository.findById(notificationId).orElseThrow();
        assertThat(impression.getCampaignId()).isEqualTo(campaign.getCampaignId());
        assertThat(impression.getTargetUserId()).isEqualTo(user.getTargetUserId());

        CampaignStats stats = campaignStatsRepository.findById(campaign.getCampaignId()).orElseThrow();
        assertThat(stats.getSentCount()).isEqualTo(1);
        assertThat(stats.getClickCount()).isZero();
    }

    @Test
    @DisplayName("노출마다 새 notificationId 가 발급되고 노출 수가 누적된다")
    void eachMatchIsANewImpression() throws Exception {
        TargetUser user = saveUser(AGE_20S);
        Campaign campaign = saveCampaign(AGE_20S, CampaignStatus.ACTIVE, daysFromNow(-1), daysFromNow(1));

        mockMvc.perform(matchRequest(user.getTargetUserId())).andExpect(status().isOk());
        mockMvc.perform(matchRequest(user.getTargetUserId())).andExpect(status().isOk());

        assertThat(adImpressionRepository.count()).isEqualTo(2);
        assertThat(campaignStatsRepository.findById(campaign.getCampaignId()).orElseThrow().getSentCount())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("다른 연령대, 진행 중이 아닌 상태, 기간 밖의 캠페인은 매칭되지 않아 204 를 돌려준다")
    void returnsNoContentWhenNothingMatches() throws Exception {
        TargetUser user = saveUser(AGE_20S);
        saveCampaign(AGE_30S, CampaignStatus.ACTIVE, daysFromNow(-1), daysFromNow(1));
        saveCampaign(AGE_20S, CampaignStatus.PAUSED, daysFromNow(-1), daysFromNow(1));
        saveCampaign(AGE_20S, CampaignStatus.ACTIVE, daysFromNow(-3), daysFromNow(-1));
        saveCampaign(AGE_20S, CampaignStatus.ACTIVE, daysFromNow(1), daysFromNow(3));

        mockMvc.perform(matchRequest(user.getTargetUserId()))
                .andExpect(status().isNoContent());

        assertThat(adImpressionRepository.count()).isZero();
        assertThat(campaignStatsRepository.count()).isZero();
    }

    @Test
    @DisplayName("노출 기간이 비어 있는 캠페인은 기간 제한 없이 매칭된다")
    void matchesCampaignWithoutPeriod() throws Exception {
        TargetUser user = saveUser(AGE_20S);
        Campaign campaign = saveCampaign(AGE_20S, CampaignStatus.ACTIVE, null, null);

        mockMvc.perform(matchRequest(user.getTargetUserId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId").value(campaign.getCampaignId()));
    }

    @Test
    @DisplayName("배너 응답의 notificationId 로 클릭을 기록할 수 있다")
    void matchedBannerCanBeClicked() throws Exception {
        TargetUser user = saveUser(AGE_20S);
        Campaign campaign = saveCampaign(AGE_20S, CampaignStatus.ACTIVE, daysFromNow(-1), daysFromNow(1));

        String responseBody = mockMvc.perform(matchRequest(user.getTargetUserId()))
                .andReturn().getResponse().getContentAsString();
        String notificationId = JsonPath.read(responseBody, "$.notificationId");

        mockMvc.perform(post("/api/v1/clicks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notificationId\": \"" + notificationId + "\"}"))
                .andExpect(status().isCreated());

        CampaignStats stats = campaignStatsRepository.findById(campaign.getCampaignId()).orElseThrow();
        assertThat(stats.getSentCount()).isEqualTo(1);
        assertThat(stats.getClickCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("존재하지 않는 유저는 404 를 돌려준다")
    void unknownUserReturnsNotFound() throws Exception {
        mockMvc.perform(matchRequest(999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TARGET_USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("targetUserId 가 없거나 숫자가 아니면 400 을 돌려준다")
    void invalidTargetUserIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/banners/match"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/v1/banners/match").param("targetUserId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private TargetUser saveUser(String ageGroup) {
        return targetUserRepository.save(TargetUser.builder()
                .ageGroup(ageGroup)
                .gender("F")
                .build());
    }

    private Campaign saveCampaign(String targetAgeGroup, CampaignStatus status,
            LocalDateTime timeStart, LocalDateTime timeEnd) {
        return campaignRepository.save(Campaign.builder()
                .userId(1L)
                .title("출근길 커피 1+1")
                .body("오전 9시 전 주문 시 1+1")
                .imageUrl("https://example.com/banner.png")
                .linkUrl("https://example.com/coffee")
                .targetAgeGroup(targetAgeGroup)
                .timeStart(timeStart)
                .timeEnd(timeEnd)
                .status(status)
                .build());
    }

    private LocalDateTime daysFromNow(int days) {
        return LocalDateTime.now().plusDays(days);
    }

    private RequestBuilder matchRequest(Long targetUserId) {
        return get("/api/v1/banners/match").param("targetUserId", String.valueOf(targetUserId));
    }
}
