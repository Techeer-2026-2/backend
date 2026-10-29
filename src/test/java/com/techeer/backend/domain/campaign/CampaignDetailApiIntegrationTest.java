package com.techeer.backend.domain.campaign;

import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.auth.jwt.JwtTokenProvider;
import com.techeer.backend.domain.campaign.entity.Campaign;
import com.techeer.backend.domain.campaign.entity.CampaignStatus;
import com.techeer.backend.domain.campaign.repository.CampaignRepository;
import com.techeer.backend.domain.stats.repository.CampaignStatsRepository;
import com.techeer.backend.global.storage.ImageStorage;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * GET·PATCH·DELETE /api/v1/campaigns/{id} 를 실제 PostgreSQL(Testcontainers)에 붙여 검증한다.
 * 이미지 저장소(S3)는 가짜로 바꿔 끼운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CampaignDetailApiIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long UNKNOWN_CAMPAIGN_ID = 999_999L;
    private static final String OLD_IMAGE_URL = "https://example.com/banner.png";
    private static final String NEW_IMAGE_URL = "https://bucket.s3.ap-northeast-2.amazonaws.com/campaign-banners/b.png";

    @MockitoBean
    private ImageStorage imageStorage;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private CampaignStatsRepository campaignStatsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        when(imageStorage.upload(anyString(), any(), any())).thenReturn(NEW_IMAGE_URL);
    }

    @AfterEach
    void tearDown() {
        campaignStatsRepository.deleteAll();
        campaignRepository.deleteAll();
    }

    @Test
    @DisplayName("캠페인 상세를 노출수·클릭수와 함께 돌려준다")
    void getsCampaignWithStats() throws Exception {
        Campaign campaign = saveOngoingCampaign();
        saveStats(campaign, 1200, 300);

        mockMvc.perform(get(campaignUrl(campaign)).header(HttpHeaders.AUTHORIZATION, bearer(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignId").value(campaign.getCampaignId()))
                .andExpect(jsonPath("$.title").value(campaign.getTitle()))
                .andExpect(jsonPath("$.status").value("ONGOING"))
                .andExpect(jsonPath("$.impressionCount").value(1200))
                .andExpect(jsonPath("$.clickCount").value(300));
    }

    @Test
    @DisplayName("아직 노출된 적 없는 캠페인은 노출수·클릭수가 0 이다")
    void getsCampaignWithoutStats() throws Exception {
        Campaign campaign = savePendingCampaign();

        mockMvc.perform(get(campaignUrl(campaign)).header(HttpHeaders.AUTHORIZATION, bearer(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.impressionCount").value(0))
                .andExpect(jsonPath("$.clickCount").value(0));
    }

    @Test
    @DisplayName("없는 캠페인은 404, 남의 캠페인은 403 을 돌려준다")
    void rejectsUnknownOrOthersCampaign() throws Exception {
        Campaign campaign = saveOngoingCampaign();
        String othersToken = bearer(OTHER_USER_ID);

        mockMvc.perform(get("/api/v1/campaigns/" + UNKNOWN_CAMPAIGN_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_NOT_FOUND"));

        mockMvc.perform(get(campaignUrl(campaign)).header(HttpHeaders.AUTHORIZATION, othersToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_ACCESS_DENIED"));

        mockMvc.perform(patchRequest(campaign, OTHER_USER_ID, "{\"title\": \"남이 바꾼 제목\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_ACCESS_DENIED"));

        mockMvc.perform(delete(campaignUrl(campaign)).header(HttpHeaders.AUTHORIZATION, othersToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_ACCESS_DENIED"));

        Campaign unchanged = reload(campaign);
        assertThat(unchanged.getTitle()).isEqualTo(campaign.getTitle());
        assertThat(unchanged.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("토큰 없이 요청하면 401 을 돌려준다")
    void rejectsUnauthenticatedRequests() throws Exception {
        Campaign campaign = saveOngoingCampaign();

        mockMvc.perform(get(campaignUrl(campaign)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        mockMvc.perform(multipart(HttpMethod.PATCH, campaignUrl(campaign))
                        .file(jsonPart("{\"title\": \"바뀐 제목\"}")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        mockMvc.perform(delete(campaignUrl(campaign)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        Campaign unchanged = reload(campaign);
        assertThat(unchanged.getTitle()).isEqualTo(campaign.getTitle());
        assertThat(unchanged.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("대기 상태 캠페인은 전체 필드를 수정할 수 있다")
    void updatesAllFieldsWhilePending() throws Exception {
        Campaign campaign = savePendingCampaign();
        LocalDateTime startAt = daysFromNow(2);
        LocalDateTime endAt = daysFromNow(5);

        mockMvc.perform(patchRequest(campaign, USER_ID, """
                        {
                          "title": "바뀐 제목",
                          "body": "바뀐 본문",
                          "linkUrl": "https://example.com/new",
                          "targetAgeGroups": ["30s"], "targetGenders": ["all"],
                          "startAt": "%s",
                          "endAt": "%s"
                        }
                        """.formatted(startAt, endAt)).file(pngImage()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("바뀐 제목"))
                .andExpect(jsonPath("$.imageUrl").value(NEW_IMAGE_URL))
                .andExpect(jsonPath("$.targetAgeGroups[0]").value("30s"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        Campaign updated = reload(campaign);
        assertThat(updated.getTitle()).isEqualTo("바뀐 제목");
        assertThat(updated.getBody()).isEqualTo("바뀐 본문");
        assertThat(updated.getImageUrl()).isEqualTo(NEW_IMAGE_URL);
        assertThat(updated.getLinkUrl()).isEqualTo("https://example.com/new");
        assertThat(updated.getAgeGroups()).containsExactly("30s");
        assertThat(updated.getTimeStart()).isEqualTo(startAt);
        assertThat(updated.getTimeEnd()).isEqualTo(endAt);
    }

    @Test
    @DisplayName("보내지 않은 필드는 그대로 두고, 빈 linkUrl 은 링크를 지운다")
    void updatesOnlyGivenFields() throws Exception {
        Campaign campaign = savePendingCampaign();

        mockMvc.perform(patchRequest(campaign, USER_ID, "{\"title\": \"제목만 바꿈\", \"linkUrl\": \"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("제목만 바꿈"))
                .andExpect(jsonPath("$.linkUrl").doesNotExist());

        Campaign updated = reload(campaign);
        assertThat(updated.getTitle()).isEqualTo("제목만 바꿈");
        assertThat(updated.getLinkUrl()).isNull();
        assertThat(updated.getBody()).isEqualTo(campaign.getBody());
        assertThat(updated.getImageUrl()).isEqualTo(campaign.getImageUrl());
        assertThat(updated.getTimeStart()).isEqualTo(campaign.getTimeStart());
        assertThat(updated.getTimeEnd()).isEqualTo(campaign.getTimeEnd());
        verify(imageStorage, never()).upload(anyString(), any(), any());
        verify(imageStorage, never()).deleteIfManaged(anyString());
    }

    @Test
    @DisplayName("request 파트 없이 이미지만 보내면 이미지만 바꾸고 이전 이미지를 지운다")
    void updatesOnlyImage() throws Exception {
        Campaign campaign = saveOngoingCampaign();

        MockMultipartHttpServletRequestBuilder imageOnly = multipart(HttpMethod.PATCH, campaignUrl(campaign))
                .file(pngImage());
        imageOnly.header(HttpHeaders.AUTHORIZATION, bearer(USER_ID));
        mockMvc.perform(imageOnly)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(NEW_IMAGE_URL));

        Campaign updated = reload(campaign);
        assertThat(updated.getImageUrl()).isEqualTo(NEW_IMAGE_URL);
        assertThat(updated.getTitle()).isEqualTo(campaign.getTitle());
        verify(imageStorage).deleteIfManaged(OLD_IMAGE_URL);
    }

    @Test
    @DisplayName("허용하지 않는 형식의 이미지는 400 을 돌려주고 업로드하지 않는다")
    void rejectsUnsupportedImageOnUpdate() throws Exception {
        Campaign campaign = savePendingCampaign();

        mockMvc.perform(patchRequest(campaign, USER_ID, "{\"title\": \"바뀐 제목\"}")
                        .file(new MockMultipartFile("image", "a.gif", "image/gif", new byte[] {1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_UNSUPPORTED_TYPE"));

        assertThat(reload(campaign).getTitle()).isEqualTo(campaign.getTitle());
        verify(imageStorage, never()).upload(anyString(), any(), any());
    }

    @Test
    @DisplayName("이미지를 올린 뒤 수정이 실패하면 새 이미지는 지우고 이전 이미지는 남긴다")
    void keepsOldImageWhenUpdateFails() throws Exception {
        Campaign campaign = savePendingCampaign();

        mockMvc.perform(patchRequest(campaign, USER_ID, "{\"startAt\": \"" + daysFromNow(10) + "\"}")
                        .file(pngImage()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CAMPAIGN_PERIOD"));

        assertThat(reload(campaign).getImageUrl()).isEqualTo(OLD_IMAGE_URL);
        verify(imageStorage).deleteIfManaged(NEW_IMAGE_URL);
        verify(imageStorage, never()).deleteIfManaged(OLD_IMAGE_URL);
    }

    @Test
    @DisplayName("진행중 캠페인은 문구와 배너 이미지만 수정할 수 있다")
    void updatesOnlyContentWhileOngoing() throws Exception {
        Campaign campaign = saveOngoingCampaign();

        mockMvc.perform(patchRequest(campaign, USER_ID, """
                        {"title": "바뀐 제목", "body": "바뀐 본문"}
                        """).file(pngImage()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("바뀐 제목"))
                .andExpect(jsonPath("$.status").value("ONGOING"));

        mockMvc.perform(patchRequest(campaign, USER_ID,
                        "{\"title\": \"반영되면 안 되는 제목\", \"endAt\": \"" + daysFromNow(10) + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_FIELD_NOT_EDITABLE"));

        mockMvc.perform(patchRequest(campaign, USER_ID, "{\"targetAgeGroups\": [\"30s\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_FIELD_NOT_EDITABLE"));

        Campaign updated = reload(campaign);
        assertThat(updated.getTitle()).isEqualTo("바뀐 제목");
        assertThat(updated.getBody()).isEqualTo("바뀐 본문");
        assertThat(updated.getImageUrl()).isEqualTo(NEW_IMAGE_URL);
        assertThat(updated.getAgeGroups()).isEqualTo(campaign.getAgeGroups());
        assertThat(updated.getTimeEnd()).isEqualTo(campaign.getTimeEnd());
    }

    @Test
    @DisplayName("종료된 캠페인은 수정할 수 없다")
    void rejectsUpdateAfterEnd() throws Exception {
        Campaign campaign = saveCampaign(daysFromNow(-3), daysFromNow(-1));

        mockMvc.perform(patchRequest(campaign, USER_ID, "{\"title\": \"바뀐 제목\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_ENDED"));

        assertThat(reload(campaign).getTitle()).isEqualTo(campaign.getTitle());
    }

    @Test
    @DisplayName("수정 결과 노출 기간이 올바르지 않거나 값이 공백이면 400 을 돌려준다")
    void rejectsInvalidUpdate() throws Exception {
        Campaign campaign = savePendingCampaign();

        mockMvc.perform(patchRequest(campaign, USER_ID,
                        "{\"title\": \"반영되면 안 되는 제목\", \"startAt\": \"" + daysFromNow(10) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CAMPAIGN_PERIOD"));

        mockMvc.perform(patchRequest(campaign, USER_ID, "{\"title\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        for (String field : List.of("title", "body")) {
            mockMvc.perform(patchRequest(campaign, USER_ID, "{\"" + field + "\": \"   \"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        // 전각 공백(U+3000)만 담긴 값도 공백으로 본다.
        for (String field : List.of("title", "body")) {
            mockMvc.perform(patchRequest(campaign, USER_ID, "{\"" + field + "\": \"　\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        mockMvc.perform(patchRequest(campaign, USER_ID,
                        "{\"startAt\": \"" + daysFromNow(-3) + "\", \"endAt\": \"" + daysFromNow(-1) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_PERIOD_ALREADY_ENDED"));

        Campaign unchanged = reload(campaign);
        assertThat(unchanged.getTitle()).isEqualTo(campaign.getTitle());
        assertThat(unchanged.getTimeStart()).isEqualTo(campaign.getTimeStart());
        assertThat(unchanged.getTimeEnd()).isEqualTo(campaign.getTimeEnd());
    }

    @Test
    @DisplayName("대기 상태 캠페인의 시작 시각을 과거로 바꾸면 바로 진행중이 된다")
    void startsImmediatelyWhenStartMovedToPast() throws Exception {
        Campaign campaign = savePendingCampaign();

        mockMvc.perform(patchRequest(campaign, USER_ID, "{\"startAt\": \"" + daysFromNow(-1) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ONGOING"));
    }

    @Test
    @DisplayName("동시에 들어온 수정은 서로의 변경을 덮어쓰지 않는다")
    void concurrentUpdatesDoNotOverwriteEachOther() throws Exception {
        Campaign campaign = saveOngoingCampaign();
        List<String> patches = List.of("{\"title\": \"동시 수정 제목\"}", "{\"body\": \"동시 수정 본문\"}");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(patches.size());
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (String json : patches) {
                results.add(executor.submit(() -> {
                    start.await();
                    return mockMvc.perform(patchRequest(campaign, USER_ID, json))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            executor.shutdownNow();
        }

        Campaign updated = reload(campaign);
        assertThat(updated.getTitle()).isEqualTo("동시 수정 제목");
        assertThat(updated.getBody()).isEqualTo("동시 수정 본문");
    }

    @Test
    @DisplayName("삭제한 캠페인은 조회·목록에서 빠지지만 행과 통계는 남는다")
    void deletesCampaign() throws Exception {
        Campaign campaign = saveOngoingCampaign();
        saveStats(campaign, 10, 2);
        String myToken = bearer(USER_ID);

        mockMvc.perform(delete(campaignUrl(campaign)).header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(campaignUrl(campaign)).header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/campaigns").header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(delete(campaignUrl(campaign)).header(HttpHeaders.AUTHORIZATION, myToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_NOT_FOUND"));

        assertThat(reload(campaign).getDeletedAt()).isNotNull();
        assertThat(campaignStatsRepository.findById(campaign.getCampaignId())).isPresent();
    }

    private Campaign savePendingCampaign() {
        return saveCampaign(daysFromNow(1), daysFromNow(3));
    }

    private Campaign saveOngoingCampaign() {
        return saveCampaign(daysFromNow(-1), daysFromNow(1));
    }

    private Campaign saveCampaign(LocalDateTime timeStart, LocalDateTime timeEnd) {
        return campaignRepository.save(Campaign.builder()
                .userId(USER_ID)
                .title("OOO 컴백 D-3")
                .body("10월 10일 오후 6시 신곡 공개")
                .imageUrl(OLD_IMAGE_URL)
                .linkUrl("https://example.com/comeback")
                .ageGroups(Set.of("20s")).genders(Set.of("all"))
                .timeStart(timeStart)
                .timeEnd(timeEnd)
                .status(CampaignStatus.ACTIVE)
                .build());
    }

    private void saveStats(Campaign campaign, long sentCount, long clickCount) {
        jdbcTemplate.update("""
                INSERT INTO campaign_stats (campaign_id, sent_count, click_count, created_at, updated_at)
                VALUES (?, ?, ?, now(), now())
                """, campaign.getCampaignId(), sentCount, clickCount);
    }

    private Campaign reload(Campaign campaign) {
        return campaignRepository.findById(campaign.getCampaignId()).orElseThrow();
    }

    // DB(timestamp)는 마이크로초까지만 저장하므로 비교가 어긋나지 않게 초 단위로 자른다.
    private LocalDateTime daysFromNow(int days) {
        return LocalDateTime.now().plusDays(days).truncatedTo(ChronoUnit.SECONDS);
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtTokenProvider.createAccessToken(userId);
    }

    private String campaignUrl(Campaign campaign) {
        return "/api/v1/campaigns/" + campaign.getCampaignId();
    }

    private MockMultipartHttpServletRequestBuilder patchRequest(Campaign campaign, Long userId, String json) {
        MockMultipartHttpServletRequestBuilder builder = multipart(HttpMethod.PATCH, campaignUrl(campaign))
                .file(jsonPart(json));
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
}
