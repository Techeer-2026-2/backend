package com.techeer.backend.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.banner.dto.BannerResponse;
import com.techeer.backend.domain.banner.repository.BannerRequestRepository;
import com.techeer.backend.domain.banner.service.BannerService;
import com.techeer.backend.domain.campaign.entity.Campaign;
import com.techeer.backend.domain.campaign.entity.CampaignStatus;
import com.techeer.backend.domain.campaign.repository.CampaignRepository;
import com.techeer.backend.domain.click.repository.ClickFallbackRepository;
import com.techeer.backend.domain.impression.repository.AdImpressionRepository;
import com.techeer.backend.domain.outbox.entity.OutboxEvent;
import com.techeer.backend.domain.outbox.repository.OutboxEventRepository;
import com.techeer.backend.domain.outbox.service.OutboxRelay;
import com.techeer.backend.domain.stats.repository.CampaignStatsRepository;
import com.techeer.backend.domain.user.entity.User;
import com.techeer.backend.domain.user.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 쓰기 경로 전체를 실제 PostgreSQL + Kafka 로 확인한다.
 * 배너 요청 → 아웃박스 → 릴레이 → ad-impressed, 클릭 → ad-clicked, 같은 요청 재시도는 한 번만 기록.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers
class WritePathIntegrationTest {

    private static final String AGE = "50s";
    private static final String GENDER = "male";

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BannerService bannerService;
    @Autowired
    private OutboxRelay outboxRelay;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private BannerRequestRepository bannerRequestRepository;
    @Autowired
    private AdImpressionRepository adImpressionRepository;
    @Autowired
    private ClickFallbackRepository clickFallbackRepository;
    @Autowired
    private CampaignRepository campaignRepository;
    @Autowired
    private CampaignStatsRepository campaignStatsRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void createTopics() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", kafka.getBootstrapServers()))) {
            for (String topic : List.of("ad-impressed", "ad-clicked")) {
                try {
                    admin.createTopics(Collections.singletonList(new NewTopic(topic, 3, (short) 1))).all().get();
                } catch (java.util.concurrent.ExecutionException e) {
                    if (!(e.getCause() instanceof TopicExistsException)) {
                        throw e;
                    }
                }
            }
        }
    }

    @AfterEach
    void tearDown() {
        outboxEventRepository.deleteAll();
        clickFallbackRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM banner_requests");
        adImpressionRepository.deleteAll();
        campaignStatsRepository.deleteAll();
        campaignRepository.deleteAll();
    }

    @Test
    void 배너_노출은_릴레이를_거쳐_ad_impressed_로_나간다() throws Exception {
        Campaign campaign = saveCampaign();
        User user = saveUser();

        BannerResponse banner = bannerService.matchBanner(user.getUserId(), UUID.randomUUID().toString())
                .orElseThrow();
        outboxRelay.relay();

        JsonNode message = awaitRecord("ad-impressed",
                node -> banner.notificationId().equals(node.get("notification_id").asText()));
        assertThat(message.get("campaign_id").asLong()).isEqualTo(campaign.getCampaignId());
        assertThat(message.get("age_group").asText()).isEqualTo(AGE);
        assertThat(message.get("gender").asText()).isEqualTo(GENDER);

        OutboxEvent row = outboxEventRepository.findAll().get(0);
        assertThat(row.getStatus()).isEqualTo(OutboxEvent.Status.PUBLISHED);
    }

    @Test
    void 클릭은_ad_clicked_로_나가고_DB_행을_만들지_않는다() throws Exception {
        saveCampaign();
        User user = saveUser();
        BannerResponse banner = bannerService.matchBanner(user.getUserId(), UUID.randomUUID().toString())
                .orElseThrow();

        mockMvc.perform(post("/api/v1/clicks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notificationId\": \"" + banner.notificationId() + "\"}"))
                .andExpect(status().isCreated());

        JsonNode message = awaitRecord("ad-clicked",
                node -> banner.notificationId().equals(node.get("notification_id").asText()));
        assertThat(message.get("target_user_id").asLong()).isEqualTo(user.getUserId());
        assertThat(campaignStatsRepository.findAll().get(0).getClickCount()).isZero();
    }

    @Test
    void 같은_요청_ID_로_다시_오면_노출과_아웃박스는_한_번만_생긴다() {
        saveCampaign();
        User user = saveUser();
        String requestId = UUID.randomUUID().toString();

        BannerResponse first = bannerService.matchBanner(user.getUserId(), requestId).orElseThrow();
        BannerResponse retry = bannerService.matchBanner(user.getUserId(), requestId).orElseThrow();

        assertThat(retry.notificationId()).isEqualTo(first.notificationId());
        assertThat(adImpressionRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
        assertThat(bannerRequestRepository.count()).isEqualTo(1);
    }

    private Campaign saveCampaign() {
        return campaignRepository.save(Campaign.builder()
                .userId(1L)
                .title("통합 테스트 캠페인")
                .body("본문")
                .imageUrl("https://example.com/banner.png")
                .ageGroups(Set.of(AGE))
                .genders(Set.of(GENDER))
                .timeStart(LocalDateTime.now().minusDays(1))
                .timeEnd(LocalDateTime.now().plusDays(1))
                .status(CampaignStatus.ACTIVE)
                .totalBudget(100_000).bidPrice(50).targetClicks(100)
                .build());
    }

    private User saveUser() {
        User user = User.create("u-" + UUID.randomUUID() + "@test.local", "x", "tester");
        user.changeDemographics(AGE, GENDER);
        return userRepository.save(user);
    }

    /** 토픽을 처음부터 읽어서 조건에 맞는 메시지를 찾는다 (최대 30초). */
    private JsonNode awaitRecord(String topic, Predicate<JsonNode> matches) throws Exception {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(
                props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topic));
            long end = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < end) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    JsonNode node = objectMapper.readTree(record.value());
                    if (matches.test(node)) {
                        return node;
                    }
                }
            }
        }
        throw new AssertionError(topic + " 에서 기대한 메시지를 찾지 못함");
    }
}
