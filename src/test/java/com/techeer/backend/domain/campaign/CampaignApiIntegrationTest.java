package com.techeer.backend.domain.campaign;

import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.auth.jwt.JwtTokenProvider;
import com.techeer.backend.domain.campaign.entity.Campaign;
import com.techeer.backend.domain.campaign.entity.CampaignStatus;
import com.techeer.backend.domain.campaign.repository.CampaignRepository;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import com.techeer.backend.global.storage.ImageStorage;
import com.techeer.backend.global.storage.ImageType;
import jakarta.servlet.ServletException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * POST·GET /api/v1/campaigns 를 실제 PostgreSQL(Testcontainers)에 붙여 검증한다.
 * 이미지 저장소(S3)는 가짜로 바꿔 끼운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CampaignApiIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final String UPLOADED_URL = "https://bucket.s3.ap-northeast-2.amazonaws.com/campaign-banners/a.png";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private java.time.Clock clock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private ImageStorage imageStorage;

    @BeforeEach
    void setUp() {
        when(imageStorage.upload(anyString(), any(), any())).thenReturn(UPLOADED_URL);
    }

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
                .andExpect(jsonPath("$.title").value("OOO 컴백 D-3"))
                .andExpect(jsonPath("$.imageUrl").value(UPLOADED_URL))
                .andExpect(jsonPath("$.linkUrl").value("https://example.com/comeback"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalBudget").value(100000))
                .andExpect(jsonPath("$.bidPrice").value(50))
                .andExpect(jsonPath("$.spentTotal").value(0))
                .andExpect(jsonPath("$.remainingBudget").value(100000))
                .andExpect(jsonPath("$.targetClicks").value(300));

        Campaign saved = campaignRepository.findAll().get(0);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getTotalBudget()).isEqualTo(100_000);
        assertThat(saved.getBidPrice()).isEqualTo(50);
        assertThat(saved.getSpentTotal()).isZero();
        assertThat(saved.getTargetClicks()).isEqualTo(300);
        assertThat(saved.getImageUrl()).isEqualTo(UPLOADED_URL);
        assertThat(saved.getAgeGroups()).containsExactly("20s");
        assertThat(saved.getTimeStart()).isEqualTo(startAt);
        assertThat(saved.getTimeEnd()).isEqualTo(endAt);
        assertThat(saved.getStatus()).isEqualTo(CampaignStatus.ACTIVE);
        verify(imageStorage).upload(eq("campaign-banners"), any(), eq(ImageType.PNG));
    }

    @Test
    @DisplayName("request 파트를 Content-Type 없이 보내도 JSON 으로 읽는다")
    void readsRequestPartWithoutContentType() throws Exception {
        MockMultipartFile requestWithoutType = new MockMultipartFile("request", null, null,
                campaignJson(daysFromNow(1), daysFromNow(3), "null").getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/campaigns")
                        .file(requestWithoutType)
                        .file(pngImage())
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_ID)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("이미지가 없거나 비었거나 허용하지 않는 형식이면 400 을 돌려주고 업로드하지 않는다")
    void rejectsInvalidImage() throws Exception {
        String json = campaignJson(daysFromNow(1), daysFromNow(3), "null");

        mockMvc.perform(multipart("/api/v1/campaigns")
                        .file(jsonPart(json))
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(createRequest(USER_ID, json, new MockMultipartFile("image", "a.png", "image/png", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_EMPTY"));

        mockMvc.perform(createRequest(USER_ID, json, new MockMultipartFile("image", "a.gif", "image/gif", new byte[] {1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_UNSUPPORTED_TYPE"));

        assertThat(campaignRepository.count()).isZero();
        verify(imageStorage, never()).upload(anyString(), any(), any());
    }

    @Test
    @DisplayName("이미지 업로드에 실패하면 502 를 돌려주고 캠페인을 저장하지 않는다")
    void rejectsWhenUploadFails() throws Exception {
        when(imageStorage.upload(anyString(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED));

        mockMvc.perform(createRequest(USER_ID, campaignJson(daysFromNow(1), daysFromNow(3), "null")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("IMAGE_UPLOAD_FAILED"));

        assertThat(campaignRepository.count()).isZero();
    }

    @Test
    @DisplayName("이미지를 올린 뒤 캠페인 저장이 실패하면 올린 이미지를 지운다")
    void deletesUploadedImageWhenSaveFails() {
        // image_url 컬럼(512자)보다 긴 주소를 돌려줘서 DB 저장을 일부러 실패시킨다.
        String tooLongUrl = UPLOADED_URL + "x".repeat(512);
        when(imageStorage.upload(anyString(), any(), any())).thenReturn(tooLongUrl);

        assertThatThrownBy(() -> mockMvc.perform(
                createRequest(USER_ID, campaignJson(daysFromNow(1), daysFromNow(3), "null"))))
                .isInstanceOf(ServletException.class);

        assertThat(campaignRepository.count()).isZero();
        verify(imageStorage).deleteIfManaged(tooLongUrl);
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
    @DisplayName("종료 시각이 시작 시각보다 뒤가 아니거나 이미 지났으면 400 을 돌려준다")
    void rejectsInvalidPeriod() throws Exception {
        LocalDateTime startAt = daysFromNow(3);

        mockMvc.perform(createRequest(USER_ID, campaignJson(startAt, daysFromNow(1), "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CAMPAIGN_PERIOD"));

        mockMvc.perform(createRequest(USER_ID, campaignJson(startAt, startAt, "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CAMPAIGN_PERIOD"));

        mockMvc.perform(createRequest(USER_ID, campaignJson(daysFromNow(-3), daysFromNow(-1), "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_PERIOD_ALREADY_ENDED"));

        assertThat(campaignRepository.count()).isZero();
        // 기간 검사는 업로드 전에 하므로 쓰이지 않을 이미지를 올리지 않는다.
        verify(imageStorage, never()).upload(anyString(), any(), any());
    }

    @Test
    @DisplayName("시작 시각이 과거면 등록 즉시 진행중이 된다")
    void startsImmediatelyWhenStartIsInPast() throws Exception {
        mockMvc.perform(createRequest(USER_ID, campaignJson(daysFromNow(-1), daysFromNow(3), "null")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ONGOING"));
    }

    @Test
    @DisplayName("타겟 목록에 null 이나 허용되지 않은 값이 있으면 400 을 돌려준다")
    void rejectsInvalidTargetValues() throws Exception {
        String base = campaignJson(daysFromNow(1), daysFromNow(3), "null");
        String nullAge = base.replace("\"targetAgeGroups\": [\"20s\"]", "\"targetAgeGroups\": [null]");
        String badGender = base.replace("\"targetGenders\": [\"all\"]", "\"targetGenders\": [\"other\"]");

        mockMvc.perform(createRequest(USER_ID, nullAge))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(createRequest(USER_ID, badGender))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("필수 값이 빠지면 400 을 돌려준다")
    void rejectsMissingRequiredFields() throws Exception {
        mockMvc.perform(createRequest(USER_ID, "{\"title\": \"OOO 컴백 D-3\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(campaignRepository.count()).isZero();
    }

    @Test
    @DisplayName("예산·입찰가·목표 클릭수가 없거나 1 보다 작으면 400 을 돌려준다")
    void rejectsMissingOrNonPositiveBilling() throws Exception {
        String base = campaignJson(daysFromNow(1), daysFromNow(3), "null");
        String billing = "\"totalBudget\": 100000, \"bidPrice\": 50, \"targetClicks\": 300";
        String[] invalid = {
                base.replace(billing, "\"bidPrice\": 50, \"targetClicks\": 300"),
                base.replace(billing, "\"totalBudget\": 100000, \"targetClicks\": 300"),
                base.replace(billing, "\"totalBudget\": 100000, \"bidPrice\": 50"),
                base.replace(billing, "\"totalBudget\": 0, \"bidPrice\": 50, \"targetClicks\": 300"),
                base.replace(billing, "\"totalBudget\": 100000, \"bidPrice\": -1, \"targetClicks\": 300"),
                base.replace(billing, "\"totalBudget\": 100000, \"bidPrice\": 50, \"targetClicks\": 0"),
        };

        for (String json : invalid) {
            mockMvc.perform(createRequest(USER_ID, json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        assertThat(campaignRepository.count()).isZero();
    }

    @Test
    @DisplayName("입찰가가 총 예산보다 크면 400 을 돌려주고 이미지를 올리지 않는다")
    void rejectsBidOverBudget() throws Exception {
        String json = campaignJson(daysFromNow(1), daysFromNow(3), "null")
                .replace("\"totalBudget\": 100000, \"bidPrice\": 50",
                        "\"totalBudget\": 1000, \"bidPrice\": 1001");

        mockMvc.perform(createRequest(USER_ID, json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_BID_EXCEEDS_BUDGET"));

        assertThat(campaignRepository.count()).isZero();
        verify(imageStorage, never()).upload(anyString(), any(), any());
    }

    @Test
    @DisplayName("입찰가가 총 예산과 같으면 등록할 수 있다")
    void acceptsBidEqualToBudget() throws Exception {
        String json = campaignJson(daysFromNow(1), daysFromNow(3), "null")
                .replace("\"totalBudget\": 100000, \"bidPrice\": 50",
                        "\"totalBudget\": 1000, \"bidPrice\": 1000");

        mockMvc.perform(createRequest(USER_ID, json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bidPrice").value(1000));
    }

    @Test
    @DisplayName("토큰 없이 요청하면 401 을 돌려준다")
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(multipart("/api/v1/campaigns")
                        .file(jsonPart(campaignJson(daysFromNow(1), daysFromNow(3), "null")))
                        .file(pngImage()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        mockMvc.perform(get("/api/v1/campaigns"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

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
                .andExpect(jsonPath("$[?(@.title == '종료')].status").value("ENDED"))
                .andExpect(jsonPath("$[0].totalBudget").value(100000))
                .andExpect(jsonPath("$[0].bidPrice").value(50))
                .andExpect(jsonPath("$[0].spentTotal").value(0))
                .andExpect(jsonPath("$[0].remainingBudget").value(100000))
                .andExpect(jsonPath("$[0].targetClicks").value(300));
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

    @Test
    @DisplayName("진행중 캠페인을 일시정지하면 PAUSED 가 되고, 재개하면 다시 ACTIVE 가 된다")
    void pauseAndResumeChangeStatus() throws Exception {
        Campaign campaign = saveCampaign(USER_ID, "정지 테스트", daysFromNow(-1), daysFromNow(1));
        mockMvc.perform(actionRequest("pause", campaign.getCampaignId(), USER_ID))
                .andExpect(status().isNoContent());
        assertThat(campaignRepository.findById(campaign.getCampaignId()).orElseThrow().getStatus())
                .isEqualTo(CampaignStatus.PAUSED);

        mockMvc.perform(actionRequest("resume", campaign.getCampaignId(), USER_ID))
                .andExpect(status().isNoContent());
        assertThat(campaignRepository.findById(campaign.getCampaignId()).orElseThrow().getStatus())
                .isEqualTo(CampaignStatus.ACTIVE);
    }

    @Test
    @DisplayName("이미 일시정지된 캠페인을 다시 일시정지하면 409 를 돌려준다")
    void pauseTwiceIsConflict() throws Exception {
        Campaign campaign = saveCampaign(USER_ID, "중복 정지", daysFromNow(-1), daysFromNow(1));
        mockMvc.perform(actionRequest("pause", campaign.getCampaignId(), USER_ID)).andExpect(status().isNoContent());
        mockMvc.perform(actionRequest("pause", campaign.getCampaignId(), USER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_STATUS_CONFLICT"));
    }

    private Campaign saveCampaign(Long userId, String title, LocalDateTime timeStart, LocalDateTime timeEnd) {
        return campaignRepository.save(Campaign.builder()
                .userId(userId)
                .title(title)
                .body("10월 10일 오후 6시 신곡 공개")
                .imageUrl("https://example.com/banner.png")
                .ageGroups(Set.of("20s")).genders(Set.of("all"))
                .timeStart(timeStart)
                .timeEnd(timeEnd)
                .status(CampaignStatus.ACTIVE)
                .totalBudget(100_000).bidPrice(50).targetClicks(300)
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
                  "title": "OOO 컴백 D-3",
                  "body": "10월 10일 오후 6시 신곡 공개",
                  "linkUrl": %s,
                  "targetAgeGroups": ["20s"], "targetGenders": ["all"],
                  "startAt": "%s",
                  "endAt": "%s",
                  "totalBudget": 100000, "bidPrice": 50, "targetClicks": 300
                }
                """.formatted(linkUrlJson, startAt, endAt);
    }

    // DB(timestamp)는 마이크로초까지만 저장하므로 비교가 어긋나지 않게 초 단위로 자른다.
    private LocalDateTime daysFromNow(int days) {
        return LocalDateTime.now().plusDays(days).truncatedTo(ChronoUnit.SECONDS);
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(userId);
    }

    private MockMultipartHttpServletRequestBuilder createRequest(Long userId, String json) {
        return createRequest(userId, json, pngImage());
    }

    private MockMultipartHttpServletRequestBuilder createRequest(Long userId, String json, MockMultipartFile image) {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/v1/campaigns")
                .file(jsonPart(json))
                .file(image);
        builder.header(HttpHeaders.AUTHORIZATION, bearer(userId));
        return builder;
    }

    private MockMultipartFile jsonPart(String json) {
        return new MockMultipartFile("request", null, MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile pngImage() {
        return new MockMultipartFile("image", "banner.png", "image/png", new byte[] {1, 2, 3});
    }

    @Test
    @DisplayName("진행중 캠페인을 재개하면 409 를 돌려준다")
    void resumeWhenActiveIsConflict() throws Exception {
        Campaign campaign = saveCampaign(USER_ID, "재개 충돌", daysFromNow(-1), daysFromNow(1));
        mockMvc.perform(actionRequest("resume", campaign.getCampaignId(), USER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_STATUS_CONFLICT"));
    }

    @Test
    @DisplayName("일시정지하면 배너 매칭에서 빠지고, 재개하면 다시 들어온다")
    void pauseRemovesFromBannerMatchingUntilResumed() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        Campaign campaign = campaignRepository.save(Campaign.builder()
                .userId(USER_ID)
                .title("배너 매칭")
                .body("본문")
                .imageUrl("https://example.com/match.png")
                .ageGroups(new java.util.HashSet<>(java.util.List.of("20s")))
                .genders(new java.util.HashSet<>(java.util.List.of("all")))
                .timeStart(now.minusDays(1))
                .timeEnd(now.plusDays(1))
                .status(CampaignStatus.ACTIVE)
                .totalBudget(100_000L)
                .bidPrice(50L)
                .targetClicks(100L)
                .build());
        Long id = campaign.getCampaignId();

        assertThat(campaignRepository.findActiveForTarget("20s", "all", now))
                .extracting(Campaign::getCampaignId).contains(id);

        mockMvc.perform(actionRequest("pause", id, USER_ID)).andExpect(status().isNoContent());
        assertThat(campaignRepository.findActiveForTarget("20s", "all", now))
                .extracting(Campaign::getCampaignId).doesNotContain(id);

        mockMvc.perform(actionRequest("resume", id, USER_ID)).andExpect(status().isNoContent());
        assertThat(campaignRepository.findActiveForTarget("20s", "all", now))
                .extracting(Campaign::getCampaignId).contains(id);
    }

    private MockHttpServletRequestBuilder actionRequest(String action, Long campaignId, Long userId) {
        return post("/api/v1/campaigns/" + campaignId + "/" + action).header(HttpHeaders.AUTHORIZATION, bearer(userId));
    }

    private MockHttpServletRequestBuilder listRequest(Long userId) {
        return get("/api/v1/campaigns").header(HttpHeaders.AUTHORIZATION, bearer(userId));
    }
}
