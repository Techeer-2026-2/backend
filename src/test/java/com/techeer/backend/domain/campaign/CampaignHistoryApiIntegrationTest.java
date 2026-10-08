package com.techeer.backend.domain.campaign;

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
import com.techeer.backend.domain.stats.repository.CampaignStatsRepository;
import java.time.Clock;
import java.time.LocalDateTime;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * GET /api/v1/campaigns/history 를 실제 PostgreSQL(Testcontainers)에 붙여 검증한다.
 *
 * <p>정렬(nulls last)과 JPQL 조건은 DB 가 판단하므로 H2 가 아닌 실제 DB 로 확인해야 의미가 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CampaignHistoryApiIntegrationTest {

    private static final String URL = "/api/v1/campaigns/history";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdvertiserRepository advertiserRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private CampaignStatsRepository campaignStatsRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private Clock clock;

    private Advertiser advertiser;
    private Advertiser other;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now(clock);
        advertiser = advertiserRepository.save(Advertiser.create("a@test.com", "hash", "테커 카페"));
        other = advertiserRepository.save(Advertiser.create("b@test.com", "hash", "다른 가게"));
    }

    /**
     * 각 테스트가 만든 캠페인·집계·광고주를 삭제해 다음 테스트를 격리한다.
     */
    @AfterEach
    void tearDown() {
        campaignStatsRepository.deleteAll();
        campaignRepository.deleteAll();
        advertiserRepository.deleteAll();
    }

    /**
     * 지난 캠페인만(ENDED 이거나 종료 시각이 지난 것) 최근에 끝난 순으로 오고, 종료 시각이 없는 것은 맨 뒤인지 검증한다.
     * 진행 중·초안·다른 광고주·삭제된 캠페인은 빠져야 한다.
     */
    @Test
    @DisplayName("지난 캠페인만 최근에 끝난 순으로 오고 종료 시각이 없는 것은 맨 뒤다")
    void returnsOnlyEndedCampaignsInOrder() throws Exception {
        Campaign endedThreeDaysAgo = save(advertiser, "3일 전 종료", CampaignStatus.ENDED, now.minusDays(3));
        Campaign expiredYesterday = save(advertiser, "어제 종료(상태는 ACTIVE)", CampaignStatus.ACTIVE, now.minusDays(1));
        Campaign endedWithoutEndTime = save(advertiser, "종료 시각 없음", CampaignStatus.ENDED, null);
        save(advertiser, "아직 진행 중", CampaignStatus.ACTIVE, now.plusDays(1));
        save(advertiser, "초안", CampaignStatus.DRAFT, now.minusDays(2));
        save(other, "다른 광고주", CampaignStatus.ENDED, now.minusDays(2));
        Campaign deleted = save(advertiser, "삭제됨", CampaignStatus.ENDED, now.minusDays(2));
        jdbcTemplate.update("UPDATE campaigns SET deleted_at = now() WHERE campaign_id = ?", deleted.getCampaignId());

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(advertiser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].campaignId").value(expiredYesterday.getCampaignId()))
                .andExpect(jsonPath("$.content[1].campaignId").value(endedThreeDaysAgo.getCampaignId()))
                .andExpect(jsonPath("$.content[2].campaignId").value(endedWithoutEndTime.getCampaignId()));
    }

    /**
     * 캠페인별 노출·클릭·클릭률이 맞게 붙고, 집계가 없는 캠페인은 0, 0, 0 인지 검증한다.
     */
    @Test
    @DisplayName("캠페인마다 노출·클릭·클릭률이 붙고 집계가 없으면 0 이다")
    void attachesStats() throws Exception {
        Campaign withStats = save(advertiser, "집계 있음", CampaignStatus.ENDED, now.minusDays(1));
        Campaign withoutStats = save(advertiser, "집계 없음", CampaignStatus.ENDED, now.minusDays(2));
        increase(withStats.getCampaignId(), 10, 2);

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(advertiser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].campaignId").value(withStats.getCampaignId()))
                .andExpect(jsonPath("$.content[0].title").value("집계 있음"))
                .andExpect(jsonPath("$.content[0].startAt").exists())
                .andExpect(jsonPath("$.content[0].endAt").exists())
                .andExpect(jsonPath("$.content[0].status").doesNotExist())
                .andExpect(jsonPath("$.content[0].timeStart").doesNotExist())
                .andExpect(jsonPath("$.content[0].sentCount").value(10))
                .andExpect(jsonPath("$.content[0].clickCount").value(2))
                .andExpect(jsonPath("$.content[0].clickRate").value(0.2))
                .andExpect(jsonPath("$.content[1].campaignId").value(withoutStats.getCampaignId()))
                .andExpect(jsonPath("$.content[1].sentCount").value(0))
                .andExpect(jsonPath("$.content[1].clickCount").value(0))
                .andExpect(jsonPath("$.content[1].clickRate").value(0.0));
    }

    /**
     * 페이지 단위로 나뉘어 오고 전체 개수·페이지 수가 맞는지 검증한다.
     */
    @Test
    @DisplayName("페이지 단위로 나뉘어 오고 전체 개수와 페이지 수가 맞다")
    void paginates() throws Exception {
        for (int i = 1; i <= 3; i++) {
            save(advertiser, "캠페인 " + i, CampaignStatus.ENDED, now.minusDays(i));
        }
        String token = bearer(advertiser);

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token).param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1));
    }

    /**
     * 범위를 벗어난 page, size 가 에러 대신 보정되는지 검증한다.
     */
    @Test
    @DisplayName("범위를 벗어난 page·size 는 보정된다")
    void clampsPageAndSize() throws Exception {
        save(advertiser, "캠페인", CampaignStatus.ENDED, now.minusDays(1));
        String token = bearer(advertiser);

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token).param("page", "-5").param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(100));

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token).param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1));
    }

    /**
     * 지난 캠페인이 하나도 없는 광고주는 빈 목록을 받는지 검증한다.
     */
    @Test
    @DisplayName("지난 캠페인이 없으면 빈 목록")
    void emptyHistory() throws Exception {
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(advertiser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    /**
     * 토큰이 없거나 올바르지 않으면 401, 토큰은 유효하지만 없거나 탈퇴한 광고주면 404 인지 검증한다.
     */
    @Test
    @DisplayName("토큰이 없거나 올바르지 않으면 401, 없거나 탈퇴한 광고주는 404")
    void rejectsBadAuthentication() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + jwtTokenProvider.createRefreshToken(advertiser.getUserId())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + jwtTokenProvider.createAccessToken(999_999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADVERTISER_NOT_FOUND"));

        String token = bearer(other);
        jdbcTemplate.update("UPDATE advertisers SET deleted_at = now() WHERE user_id = ?", other.getUserId());
        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADVERTISER_NOT_FOUND"));
    }

    /**
     * 다른 광고주의 ID 를 파라미터로 보내도 무시되고 토큰의 광고주 캠페인만 오는지 검증한다.
     */
    @Test
    @DisplayName("다른 광고주 ID 를 파라미터로 보내도 무시되고 내 캠페인만 온다")
    void ignoresAdvertiserIdParameter() throws Exception {
        Campaign mine = save(advertiser, "내 캠페인", CampaignStatus.ENDED, now.minusDays(1));
        save(other, "남의 캠페인", CampaignStatus.ENDED, now.minusDays(1));

        mockMvc.perform(get(URL)
                        .param("advertiserId", String.valueOf(other.getUserId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(advertiser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].campaignId").value(mine.getCampaignId()));
    }

    private String bearer(Advertiser owner) {
        return "Bearer " + jwtTokenProvider.createAccessToken(owner.getUserId());
    }

    private Campaign save(Advertiser owner, String title, CampaignStatus status, LocalDateTime timeEnd) {
        return campaignRepository.save(Campaign.builder()
                .userId(owner.getUserId())
                .title(title)
                .targetAgeGroup("20s")
                .timeStart(now.minusDays(10))
                .timeEnd(timeEnd)
                .status(status)
                .build());
    }

    private void increase(long campaignId, int sent, int click) {
        transactionTemplate.executeWithoutResult(status -> {
            for (int i = 0; i < sent; i++) {
                campaignStatsRepository.increaseSentCount(campaignId, now);
            }
            for (int i = 0; i < click; i++) {
                campaignStatsRepository.increaseClickCount(campaignId, now);
            }
        });
    }
}
