package com.techeer.backend.domain.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.campaign.entity.Campaign;
import com.techeer.backend.domain.campaign.entity.CampaignStatus;
import com.techeer.backend.domain.campaign.repository.CampaignRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * POST·GET /api/v1/campaigns 를 실제 PostgreSQL(Testcontainers)에 붙여 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CampaignApiIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        campaignRepository.deleteAll();
    }

    @Test
    @DisplayName("캠페인을 등록하면 201 과 함께 대기 상태로 돌려주고 ACTIVE 로 저장한다")
    void createsCampaign() throws Exception {
        LocalDateTime startAt = daysFromNow(1);
        LocalDateTime endAt = daysFromNow(3);

        mockMvc.perform(createRequest(USER_ID, campaignJson(startAt, endAt, "\"https://example.com/comeback\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.campaignId").isNumber())
                .andExpect(jsonPath("$.artistId").value(12))
                .andExpect(jsonPath("$.title").value("OOO 컴백 D-3"))
                .andExpect(jsonPath("$.linkUrl").value("https://example.com/comeback"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        Campaign saved = campaignRepository.findAll().get(0);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getArtistId()).isEqualTo(12L);
        assertThat(saved.getTargetAgeGroup()).isEqualTo("20s");
        assertThat(saved.getTimeStart()).isEqualTo(startAt);
        assertThat(saved.getTimeEnd()).isEqualTo(endAt);
        assertThat(saved.getStatus()).isEqualTo(CampaignStatus.ACTIVE);
    }

    @Test
    @DisplayName("linkUrl 없이도 캠페인을 등록할 수 있다")
    void createsCampaignWithoutLinkUrl() throws Exception {
        mockMvc.perform(createRequest(USER_ID, campaignJson(daysFromNow(1), daysFromNow(3), "null")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.linkUrl").doesNotExist());

        assertThat(campaignRepository.findAll().get(0).getLinkUrl()).isNull();
    }

    @Test
    @DisplayName("종료 시각이 시작 시각보다 뒤가 아니면 400 을 돌려준다")
    void rejectsInvalidPeriod() throws Exception {
        LocalDateTime startAt = daysFromNow(3);

        mockMvc.perform(createRequest(USER_ID, campaignJson(startAt, daysFromNow(1), "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CAMPAIGN_PERIOD"));

        mockMvc.perform(createRequest(USER_ID, campaignJson(startAt, startAt, "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CAMPAIGN_PERIOD"));

        assertThat(campaignRepository.count()).isZero();
    }

    @Test
    @DisplayName("필수 값이 빠지면 400 을 돌려준다")
    void rejectsMissingRequiredFields() throws Exception {
        mockMvc.perform(createRequest(USER_ID, "{\"title\": \"OOO 컴백 D-3\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(post("/api/v1/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(campaignJson(daysFromNow(1), daysFromNow(3), "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(campaignRepository.count()).isZero();
    }

    @Test
    @DisplayName("내 캠페인만 노출 기간으로 계산한 상태와 함께 돌려준다")
    void listsOnlyMyCampaignsWithProgress() throws Exception {
        saveCampaign(USER_ID, "대기", daysFromNow(1), daysFromNow(3));
        saveCampaign(USER_ID, "진행중", daysFromNow(-1), daysFromNow(1));
        saveCampaign(USER_ID, "종료", daysFromNow(-3), daysFromNow(-1));
        saveCampaign(OTHER_USER_ID, "남의 캠페인", daysFromNow(-1), daysFromNow(1));

        mockMvc.perform(listRequest(USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.title == '남의 캠페인')]").isEmpty())
                .andExpect(jsonPath("$[?(@.title == '대기')].status").value("PENDING"))
                .andExpect(jsonPath("$[?(@.title == '진행중')].status").value("ONGOING"))
                .andExpect(jsonPath("$[?(@.title == '종료')].status").value("ENDED"));
    }

    @Test
    @DisplayName("목록은 최근 등록순으로 돌려준다")
    void listsNewestFirst() throws Exception {
        // 저장 순서와 등록 시각 순서를 다르게 해서 정렬이 created_at 기준인지 확인한다.
        Campaign oldest = saveCampaign(USER_ID, "가장 오래됨", daysFromNow(-1), daysFromNow(1));
        Campaign newest = saveCampaign(USER_ID, "가장 최근", daysFromNow(-1), daysFromNow(1));
        Campaign middle = saveCampaign(USER_ID, "중간", daysFromNow(-1), daysFromNow(1));
        setCreatedAt(oldest, daysFromNow(-3));
        setCreatedAt(middle, daysFromNow(-2));
        setCreatedAt(newest, daysFromNow(-1));

        mockMvc.perform(listRequest(USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].title").value("가장 최근"))
                .andExpect(jsonPath("$[1].title").value("중간"))
                .andExpect(jsonPath("$[2].title").value("가장 오래됨"));
    }

    @Test
    @DisplayName("status 를 주면 해당 상태의 캠페인만 돌려준다")
    void filtersByProgress() throws Exception {
        saveCampaign(USER_ID, "대기", daysFromNow(1), daysFromNow(3));
        saveCampaign(USER_ID, "진행중", daysFromNow(-1), daysFromNow(1));
        saveCampaign(USER_ID, "종료", daysFromNow(-3), daysFromNow(-1));

        mockMvc.perform(listRequest(USER_ID).param("status", "ONGOING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("진행중"));

        mockMvc.perform(listRequest(USER_ID).param("status", "ENDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("종료"));
    }

    @Test
    @DisplayName("캠페인이 없으면 빈 목록을, 잘못된 status 는 400 을 돌려준다")
    void emptyListAndInvalidStatus() throws Exception {
        mockMvc.perform(listRequest(USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(listRequest(USER_ID).param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private Campaign saveCampaign(Long userId, String title, LocalDateTime timeStart, LocalDateTime timeEnd) {
        return campaignRepository.save(Campaign.builder()
                .userId(userId)
                .artistId(12L)
                .title(title)
                .body("10월 10일 오후 6시 신곡 공개")
                .imageUrl("https://example.com/banner.png")
                .targetAgeGroup("20s")
                .timeStart(timeStart)
                .timeEnd(timeEnd)
                .status(CampaignStatus.ACTIVE)
                .build());
    }

    // created_at 은 감사(auditing)로 채워지고 수정할 수 없는 컬럼이라 SQL 로 직접 바꾼다.
    private void setCreatedAt(Campaign campaign, LocalDateTime createdAt) {
        jdbcTemplate.update("UPDATE campaigns SET created_at = ? WHERE campaign_id = ?",
                Timestamp.valueOf(createdAt), campaign.getCampaignId());
    }

    private String campaignJson(LocalDateTime startAt, LocalDateTime endAt, String linkUrlJson) {
        return """
                {
                  "artistId": 12,
                  "title": "OOO 컴백 D-3",
                  "body": "10월 10일 오후 6시 신곡 공개",
                  "imageUrl": "https://example.com/banner.png",
                  "linkUrl": %s,
                  "targetAgeGroup": "20s",
                  "startAt": "%s",
                  "endAt": "%s"
                }
                """.formatted(linkUrlJson, startAt, endAt);
    }

    // DB(timestamp)는 마이크로초까지만 저장하므로 비교가 어긋나지 않게 초 단위로 자른다.
    private LocalDateTime daysFromNow(int days) {
        return LocalDateTime.now().plusDays(days).truncatedTo(ChronoUnit.SECONDS);
    }

    private RequestBuilder createRequest(Long userId, String json) {
        return post("/api/v1/campaigns")
                .param("userId", String.valueOf(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private MockHttpServletRequestBuilder listRequest(Long userId) {
        return get("/api/v1/campaigns").param("userId", String.valueOf(userId));
    }
}
