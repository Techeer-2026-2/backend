package com.techeer.backend.domain.click;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.click.repository.ClickEventRepository;
import com.techeer.backend.domain.click.service.ClickService;
import com.techeer.backend.domain.impression.entity.AdImpression;
import com.techeer.backend.domain.impression.repository.AdImpressionRepository;
import com.techeer.backend.domain.stats.repository.CampaignStatsRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * POST /api/v1/clicks 를 실제 PostgreSQL(Testcontainers)에 붙여 검증한다.
 *
 * <p>멱등성은 UNIQUE 제약과 ON CONFLICT 에 기대므로 H2 가 아닌 실제 DB 로 테스트해야 의미가 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ClickApiIntegrationTest {

    private static final String NOTIFICATION_ID = "3f2b8c1e-9a4d-4e7b-8c21-5d6f7a8b9c0d";
    private static final long CAMPAIGN_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClickService clickService;

    @Autowired
    private AdImpressionRepository adImpressionRepository;

    @Autowired
    private ClickEventRepository clickEventRepository;

    @Autowired
    private CampaignStatsRepository campaignStatsRepository;

    /**
     * 각 테스트가 저장한 클릭, 캠페인 집계와 노출을 삭제해 다음 테스트를 격리한다.
     */
    @AfterEach
    void tearDown() {
        clickEventRepository.deleteAll();
        campaignStatsRepository.deleteAll();
        adImpressionRepository.deleteAll();
    }

    /**
     * 첫 클릭이 201 응답과 함께 한 건 저장되고 캠페인 클릭 수가 1이 되는지 검증한다.
     */
    @Test
    @DisplayName("첫 클릭은 201 로 기록되고 캠페인 클릭 수가 1 오른다")
    void firstClickIsRecorded() throws Exception {
        saveImpression(NOTIFICATION_ID);

        mockMvc.perform(clickRequest(NOTIFICATION_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clickId").isNumber())
                .andExpect(jsonPath("$.notificationId").value(NOTIFICATION_ID))
                .andExpect(jsonPath("$.campaignId").value(CAMPAIGN_ID))
                .andExpect(jsonPath("$.clickedAt").exists());

        assertThat(clickEventRepository.count()).isEqualTo(1);
        assertThat(clickCount()).isEqualTo(1);
    }

    /**
     * 중복 클릭이 기존 클릭 ID와 200을 반환하고 저장 건수와 집계를 유지하는지 검증한다.
     */
    @Test
    @DisplayName("같은 notificationId 로 다시 클릭하면 200 과 기존 클릭을 돌려주고 집계는 그대로다")
    void duplicateClickIsIdempotent() throws Exception {
        saveImpression(NOTIFICATION_ID);
        mockMvc.perform(clickRequest(NOTIFICATION_ID)).andExpect(status().isCreated());
        Long firstClickId = clickEventRepository.findByNotificationId(NOTIFICATION_ID).orElseThrow().getClickId();

        mockMvc.perform(clickRequest(NOTIFICATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickId").value(firstClickId));

        assertThat(clickEventRepository.count()).isEqualTo(1);
        assertThat(clickCount()).isEqualTo(1);
    }

    /**
     * 같은 캠페인의 서로 다른 노출을 클릭하면 클릭 수가 누적되는지 검증한다.
     */
    @Test
    @DisplayName("서로 다른 노출의 클릭은 같은 캠페인 집계에 누적된다")
    void clicksAccumulatePerCampaign() throws Exception {
        saveImpression("notification-a");
        saveImpression("notification-b");

        mockMvc.perform(clickRequest("notification-a")).andExpect(status().isCreated());
        mockMvc.perform(clickRequest("notification-b")).andExpect(status().isCreated());

        assertThat(clickCount()).isEqualTo(2);
    }

    /**
     * 동일 노출에 대한 동시 요청 중 하나만 클릭을 생성하고 집계도 한 번만 증가하는지 검증한다.
     */
    @Test
    @DisplayName("같은 클릭이 동시에 여러 번 들어와도 한 번만 기록된다")
    void concurrentDuplicateClicksAreRecordedOnce() throws Exception {
        saveImpression(NOTIFICATION_ID);
        int requestCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch startSignal = new CountDownLatch(1);

        int createdCount = 0;
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < requestCount; i++) {
                Callable<Boolean> task = () -> {
                    startSignal.await();
                    return clickService.recordClick(NOTIFICATION_ID).created();
                };
                results.add(executor.submit(task));
            }
            startSignal.countDown();

            for (Future<Boolean> result : results) {
                if (result.get()) {
                    createdCount++;
                }
            }
        } finally {
            // 실패하거나 인터럽트돼도 남은 작업이 끝난 뒤에 tearDown 이 DB 를 비우도록 기다린다.
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(createdCount).isEqualTo(1);
        assertThat(clickEventRepository.count()).isEqualTo(1);
        assertThat(clickCount()).isEqualTo(1);
    }

    /**
     * 없는 알림 ID가 404와 IMPRESSION_NOT_FOUND를 반환하고 클릭을 저장하지 않는지 검증한다.
     */
    @Test
    @DisplayName("존재하지 않는 notificationId 는 404 를 돌려준다")
    void unknownNotificationReturnsNotFound() throws Exception {
        mockMvc.perform(clickRequest("no-such-notification"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMPRESSION_NOT_FOUND"));

        assertThat(clickEventRepository.count()).isZero();
    }

    /**
     * 빈 알림 ID가 요청 검증에서 거부되어 400과 INVALID_INPUT을 반환하는지 검증한다.
     */
    @Test
    @DisplayName("notificationId 가 비어 있으면 400 을 돌려준다")
    void blankNotificationReturnsBadRequest() throws Exception {
        mockMvc.perform(clickRequest(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    /**
     * 지정한 알림 ID로 공통 테스트 캠페인과 사용자에 대한 노출을 저장한다.
     */
    private void saveImpression(String notificationId) {
        adImpressionRepository.save(AdImpression.builder()
                .notificationId(notificationId)
                .campaignId(CAMPAIGN_ID)
                .targetUserId(100L)
                .shownAt(LocalDateTime.now())
                .build());
    }

    /**
     * 공통 테스트 캠페인의 집계 행을 조회해 현재 클릭 수를 반환한다.
     */
    private long clickCount() {
        return campaignStatsRepository.findById(CAMPAIGN_ID).orElseThrow().getClickCount();
    }

    /**
     * 지정한 알림 ID를 JSON 본문에 담은 클릭 기록 POST 요청을 생성한다.
     */
    private RequestBuilder clickRequest(String notificationId) {
        return post("/api/v1/clicks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notificationId\": \"" + notificationId + "\"}");
    }
}
