package com.techeer.backend.domain.click;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.click.entity.ClickFallback;
import com.techeer.backend.domain.click.repository.ClickEventRepository;
import com.techeer.backend.domain.click.repository.ClickFallbackRepository;
import com.techeer.backend.domain.click.service.ClickFallbackService;
import com.techeer.backend.domain.impression.entity.AdImpression;
import com.techeer.backend.domain.impression.repository.AdImpressionRepository;
import com.techeer.backend.domain.outbox.EventPublishException;
import com.techeer.backend.domain.outbox.EventPublisher;
import com.techeer.backend.domain.stats.repository.CampaignStatsRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * POST /api/v1/clicks 를 검증한다. 클릭은 DB 에 쓰지 않고 Kafka ad-clicked 로 보내므로
 * Kafka 발행은 목으로 바꿔 호출 내용을 확인한다 (실제 Kafka 동작은 EventPublisherTest 와 통합 테스트에서 본다).
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
    private AdImpressionRepository adImpressionRepository;

    @Autowired
    private ClickEventRepository clickEventRepository;

    @Autowired
    private CampaignStatsRepository campaignStatsRepository;

    @MockitoBean
    private EventPublisher eventPublisher;

    @MockitoSpyBean
    private ClickFallbackService clickFallbackService;

    @Autowired
    private ClickFallbackRepository clickFallbackRepository;

    @AfterEach
    void tearDown() {
        clickEventRepository.deleteAll();
        campaignStatsRepository.deleteAll();
        adImpressionRepository.deleteAll();
        clickFallbackRepository.deleteAll();
    }

    @Test
    @DisplayName("클릭은 201 로 응답하고 ad-clicked 에 같은 키로 보낸다")
    void clickIsPublishedAndAcceptedWithCreated() throws Exception {
        saveImpression(NOTIFICATION_ID);

        mockMvc.perform(clickRequest(NOTIFICATION_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notificationId").value(NOTIFICATION_ID))
                .andExpect(jsonPath("$.campaignId").value(CAMPAIGN_ID))
                .andExpect(jsonPath("$.clickId").doesNotExist());

        verify(eventPublisher).publish(eq("ad-clicked"), eq(String.valueOf(CAMPAIGN_ID)),
                contains("\"notification_id\":\"" + NOTIFICATION_ID + "\""));
    }

    @Test
    @DisplayName("같은 클릭이 다시 와도 201 이고 중복 제거 없이 다시 보낸다 (재계산이 정리)")
    void duplicateClickIsAcceptedAgain() throws Exception {
        saveImpression(NOTIFICATION_ID);

        mockMvc.perform(clickRequest(NOTIFICATION_ID)).andExpect(status().isCreated());
        mockMvc.perform(clickRequest(NOTIFICATION_ID)).andExpect(status().isCreated());

        verify(eventPublisher, times(2)).publish(any(), any(), any());
    }

    @Test
    @DisplayName("클릭은 DB 에 행을 만들지 않고 캠페인 클릭 수도 올리지 않는다")
    void clickDoesNotWriteToDatabase() throws Exception {
        saveImpression(NOTIFICATION_ID);

        mockMvc.perform(clickRequest(NOTIFICATION_ID)).andExpect(status().isCreated());

        assertThat(clickEventRepository.count()).isZero();
        assertThat(campaignStatsRepository.findById(CAMPAIGN_ID).map(s -> s.getClickCount()).orElse(0L)).isZero();
    }

    @Test
    @DisplayName("없는 notificationId 는 404 이고 아무것도 보내지 않는다")
    void unknownNotificationReturnsNotFound() throws Exception {
        mockMvc.perform(clickRequest("no-such-notification"))
                .andExpect(status().isNotFound());

        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("빈 notificationId 는 400")
    void blankNotificationReturnsBadRequest() throws Exception {
        mockMvc.perform(clickRequest(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Kafka 전송이 실패하면 202 로 응답하고 실패 표에 보관한다")
    void publishFailureIsStoredAndAccepted() throws Exception {
        saveImpression(NOTIFICATION_ID);
        doThrow(new EventPublishException("끊김", null)).when(eventPublisher).publish(any(), any(), any());

        mockMvc.perform(clickRequest(NOTIFICATION_ID))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.notificationId").value(NOTIFICATION_ID));

        assertThat(clickFallbackRepository.findTop500ByStatusOrderByFallbackIdAsc(ClickFallback.Status.PENDING))
                .hasSize(1);
        assertThat(clickFallbackRepository.findAll().get(0).getNotificationId()).isEqualTo(NOTIFICATION_ID);
    }

    @Test
    @DisplayName("전송도 보관도 실패하면 503 CLICK_PUBLISH_FAILED")
    void publishAndStoreFailureReturnsServiceUnavailable() throws Exception {
        saveImpression(NOTIFICATION_ID);
        doThrow(new EventPublishException("끊김", null)).when(eventPublisher).publish(any(), any(), any());
        doThrow(new RuntimeException("DB 도 안 됨")).when(clickFallbackService).save(any(), any());

        mockMvc.perform(clickRequest(NOTIFICATION_ID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CLICK_PUBLISH_FAILED"));
    }

    private void saveImpression(String notificationId) {
        adImpressionRepository.save(AdImpression.builder()
                .notificationId(notificationId)
                .campaignId(CAMPAIGN_ID)
                .userId(100L)
                .shownAt(LocalDateTime.now())
                .ageGroup("20s")
                .gender("female")
                .build());
    }

    private RequestBuilder clickRequest(String notificationId) {
        return post("/api/v1/clicks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notificationId\": \"" + notificationId + "\"}");
    }
}
