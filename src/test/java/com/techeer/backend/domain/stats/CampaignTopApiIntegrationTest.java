package com.techeer.backend.domain.stats;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.advertiser.entity.Advertiser;
import com.techeer.backend.domain.advertiser.repository.AdvertiserRepository;
import com.techeer.backend.domain.auth.jwt.JwtTokenProvider;
import com.techeer.backend.domain.campaign.entity.Campaign;
import com.techeer.backend.domain.campaign.entity.CampaignStatus;
import com.techeer.backend.domain.campaign.repository.CampaignRepository;
import com.techeer.backend.global.config.ClickHouseConfig;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET /api/v1/stats/top-campaigns 를 실제 PostgreSQL, ClickHouse(Testcontainers)에 붙여 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CampaignTopApiIntegrationTest {

    private static final String URL = "/api/v1/stats/top-campaigns";
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdvertiserRepository advertiserRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private Clock clock;

    @Autowired
    @Qualifier(ClickHouseConfig.QUALIFIER)
    private NamedParameterJdbcTemplate clickHouseJdbc;

    private Long ownerId;
    private Long otherId;

    @BeforeEach
    void setUp() {
        ownerId = advertiserRepository.save(Advertiser.create("owner@test.com", "hash", "테커 카페")).getUserId();
        otherId = advertiserRepository.save(Advertiser.create("other@test.com", "hash", "다른 가게")).getUserId();
    }

    @AfterEach
    void tearDown() {
        campaignRepository.deleteAll();
        advertiserRepository.deleteAll();
        clickHouseJdbc.getJdbcTemplate().execute("TRUNCATE TABLE IF EXISTS click_facts");
        clickHouseJdbc.getJdbcTemplate().execute("TRUNCATE TABLE IF EXISTS click_facts_hour");
        clickHouseJdbc.getJdbcTemplate().execute("TRUNCATE TABLE IF EXISTS click_facts_day");
    }

    @Test
    @DisplayName("기간 안의 클릭이 많은 내 캠페인 순으로 오고, 기간 밖 클릭과 남의 캠페인은 빠진다")
    void ranksMyCampaignsByRecentClicks() throws Exception {
        Campaign few = saveCampaign(ownerId, "클릭 적음");
        Campaign many = saveCampaign(ownerId, "클릭 많음");
        saveCampaign(ownerId, "클릭 없음");
        Campaign others = saveCampaign(otherId, "남의 캠페인");
        LocalDateTime now = LocalDateTime.now(clock);

        addClicks(few, now.minusMinutes(2), 3);
        addClicks(few, now.minusHours(2), 100);     // 기간 밖
        addClicks(many, now.minusMinutes(5), 2);
        addClicks(many, now.minusMinutes(1), 3);    // 병합 전 행 2개가 합산되는지
        addClicks(others, now.minusMinutes(1), 50); // 남의 캠페인

        mockMvc.perform(get(URL).param("windowMinutes", "10").header(HttpHeaders.AUTHORIZATION, bearer(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].campaignId").value(many.getCampaignId()))
                .andExpect(jsonPath("$[0].title").value("클릭 많음"))
                .andExpect(jsonPath("$[0].clickCount").value(5))
                .andExpect(jsonPath("$[1].campaignId").value(few.getCampaignId()))
                .andExpect(jsonPath("$[1].clickCount").value(3));
    }

    @Test
    @DisplayName("limit 만큼만 돌려준다")
    void respectsLimit() throws Exception {
        Campaign first = saveCampaign(ownerId, "1등");
        Campaign second = saveCampaign(ownerId, "2등");
        LocalDateTime now = LocalDateTime.now(clock);
        addClicks(first, now.minusMinutes(1), 9);
        addClicks(second, now.minusMinutes(1), 1);

        mockMvc.perform(get(URL).param("windowMinutes", "5").param("limit", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].campaignId").value(first.getCampaignId()));
    }

    @Test
    @DisplayName("캠페인이나 클릭이 없으면 빈 배열")
    void emptyWhenNoClicks() throws Exception {
        mockMvc.perform(get(URL).param("windowMinutes", "60").header(HttpHeaders.AUTHORIZATION, bearer(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("windowMinutes 가 1~60 밖이거나 없으면, limit 이 1~20 밖이면 400")
    void rejectsOutOfRange() throws Exception {
        String token = bearer(ownerId);
        mockMvc.perform(get(URL).param("windowMinutes", "0").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get(URL).param("windowMinutes", "61").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get(URL).param("windowMinutes", "10").param("limit", "21")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("토큰이 없으면 401, 없는 광고주면 404")
    void rejectsBadAuthentication() throws Exception {
        mockMvc.perform(get(URL).param("windowMinutes", "10"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL).param("windowMinutes", "10").header(HttpHeaders.AUTHORIZATION, bearer(999_999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADVERTISER_NOT_FOUND"));
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(userId);
    }

    private Campaign saveCampaign(Long userId, String title) {
        return campaignRepository.save(Campaign.builder()
                .userId(userId)
                .title(title)
                .body("본문")
                .ageGroups(Set.of("20s"))
                .genders(Set.of("all"))
                .status(CampaignStatus.ACTIVE)
                .totalBudget(100_000).bidPrice(50).targetClicks(300)
                .build());
    }

    private void addClicks(Campaign campaign, LocalDateTime at, long count) {
        clickHouseJdbc.update("""
                INSERT INTO click_facts (campaign_id, minute, age_group, gender, cnt)
                VALUES (:campaignId, toStartOfMinute(toDateTime(:at)), '20s', 'female', :count)
                """, Map.of("campaignId", campaign.getCampaignId(), "at", at.format(FORMAT), "count", count));
    }
}
