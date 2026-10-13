package com.techeer.backend.domain.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.auth.jwt.JwtTokenProvider;
import com.techeer.backend.domain.campaign.entity.Campaign;
import com.techeer.backend.domain.campaign.entity.CampaignStatus;
import com.techeer.backend.domain.campaign.repository.CampaignRepository;
import com.techeer.backend.domain.stats.repository.CampaignStatsRepository;
import com.techeer.backend.domain.stats.service.CampaignStatsStreamService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * GET /api/v1/campaigns/{id}/stats/stream 을 실제 서버와 PostgreSQL(Testcontainers)에 붙여 검증한다.
 *
 * <p>SSE 는 응답이 끝나지 않고 계속 이어지므로 MockMvc 대신 실제 HTTP 연결로 이벤트를 읽는다.
 * 전송 간격은 테스트가 빨리 끝나도록 200ms 로 줄인다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "stats.stream.interval-ms=200")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class CampaignStatsStreamApiIntegrationTest {

    /** saveCampaign() 이 만드는 캠페인의 소유 광고주 ID. */
    private static final long OWNER_ID = 1L;
    private static final long OTHER_ADVERTISER_ID = 2L;

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private CampaignStatsRepository campaignStatsRepository;

    @Autowired
    private CampaignStatsStreamService campaignStatsStreamService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private Clock clock;

    /**
     * 각 테스트가 만든 캠페인과 집계를 삭제해 다음 테스트를 격리한다.
     */
    @AfterEach
    void tearDown() {
        campaignStatsRepository.deleteAll();
        campaignRepository.deleteAll();
    }

    /**
     * 연결 직후 현재 노출·클릭 수와 클릭률이 첫 이벤트로 오는지 검증한다.
     */
    @Test
    @DisplayName("연결하면 현재 노출·클릭 수를 바로 보내준다")
    void sendsCurrentStatsImmediately() throws Exception {
        long campaignId = saveCampaign().getCampaignId();
        increaseSent(campaignId, 4);
        increaseClick(campaignId, 1);

        try (Stream<String> lines = openStream(campaignId)) {
            String data = nextData(lines.iterator());

            assertThat(data).contains("\"campaignId\":" + campaignId);
            assertThat(data).contains("\"sentCount\":4");
            assertThat(data).contains("\"clickCount\":1");
            assertThat(data).contains("\"clickRate\":0.25");
        }
    }

    /**
     * 연결을 유지한 채 노출이 늘면 이후 이벤트에 늘어난 값이 실려 오는지 검증한다.
     */
    @Test
    @DisplayName("연결을 유지하면 값이 바뀔 때마다 새 통계가 push 된다")
    void pushesUpdatedStats() throws Exception {
        long campaignId = saveCampaign().getCampaignId();
        increaseSent(campaignId, 1);

        try (Stream<String> lines = openStream(campaignId)) {
            Iterator<String> iterator = lines.iterator();
            assertThat(nextData(iterator)).contains("\"sentCount\":1");

            increaseSent(campaignId, 1);

            String data = nextData(iterator);
            while (!data.contains("\"sentCount\":2")) {
                data = nextData(iterator);
            }
            assertThat(data).contains("\"sentCount\":2");
        }
    }

    /**
     * 집계 행이 아직 없는 캠페인이 0, 0 으로 응답되고 0 으로 나누지 않는지 검증한다.
     */
    @Test
    @DisplayName("노출이 한 번도 없는 캠페인은 0, 0 으로 보내고 클릭률도 0 이다")
    void campaignWithoutStatsIsZero() throws Exception {
        long campaignId = saveCampaign().getCampaignId();

        try (Stream<String> lines = openStream(campaignId)) {
            String data = nextData(lines.iterator());

            assertThat(data).contains("\"sentCount\":0");
            assertThat(data).contains("\"clickCount\":0");
            assertThat(data).contains("\"clickRate\":0.0");
        }
    }

    /**
     * 없는 캠페인은 EventSource 가 보내는 Accept: text/event-stream 요청에도 JSON 에러 본문과 404 를 받는지 검증한다.
     */
    @Test
    @DisplayName("없는 캠페인은 404 CAMPAIGN_NOT_FOUND 를 돌려준다")
    void unknownCampaignIsNotFound() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                request(999_999L), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("CAMPAIGN_NOT_FOUND");
    }

    /**
     * 토큰이 없거나 올바르지 않으면 연결 전에 401 이고, EventSource 가 보내는 Accept: text/event-stream 요청에도
     * 에러 본문이 JSON 으로 오는지 검증한다.
     */
    @Test
    @DisplayName("토큰이 없으면 401 AUTH_REQUIRED, 올바르지 않거나 refresh token 이면 401 INVALID_TOKEN")
    void requiresAuthentication() throws Exception {
        long campaignId = saveCampaign().getCampaignId();
        awaitActiveSubscriptions(0);     // 앞 테스트가 남긴 구독이 정리될 때까지 기다린 뒤 시작

        HttpResponse<String> noToken = getAsString(request(campaignId, null));
        assertThat(noToken.statusCode()).isEqualTo(401);
        assertThat(noToken.body()).contains("AUTH_REQUIRED");

        HttpResponse<String> garbage = getAsString(request(campaignId, "Bearer not-a-jwt"));
        assertThat(garbage.statusCode()).isEqualTo(401);
        assertThat(garbage.body()).contains("INVALID_TOKEN");

        HttpResponse<String> refreshToken = getAsString(
                request(campaignId, "Bearer " + jwtTokenProvider.createRefreshToken(OWNER_ID)));
        assertThat(refreshToken.statusCode()).isEqualTo(401);
        assertThat(refreshToken.body()).contains("INVALID_TOKEN");

        assertThat(campaignStatsStreamService.activeSubscriptionCount()).isZero();
    }

    /**
     * 다른 광고주의 캠페인과 삭제된 캠페인은 없는 캠페인과 똑같은 404 인지(남의 캠페인 존재를 알 수 없는지) 검증한다.
     */
    @Test
    @DisplayName("다른 광고주의 캠페인과 삭제된 캠페인은 없는 캠페인과 똑같이 404")
    void otherAdvertisersAndDeletedCampaignsAreNotFound() throws Exception {
        long campaignId = saveCampaign().getCampaignId();
        awaitActiveSubscriptions(0);
        String otherToken = "Bearer " + jwtTokenProvider.createAccessToken(OTHER_ADVERTISER_ID);

        HttpResponse<String> notMine = getAsString(request(campaignId, otherToken));
        HttpResponse<String> missing = getAsString(request(999_999L, otherToken));
        assertThat(notMine.statusCode()).isEqualTo(404);
        assertThat(notMine.body()).isEqualTo(missing.body());

        jdbcTemplate.update("UPDATE campaigns SET deleted_at = now() WHERE campaign_id = ?", campaignId);
        HttpResponse<String> deleted = getAsString(request(campaignId));
        assertThat(deleted.statusCode()).isEqualTo(404);
        assertThat(deleted.body()).contains("CAMPAIGN_NOT_FOUND");

        assertThat(campaignStatsStreamService.activeSubscriptionCount()).isZero();
    }

    /**
     * 클라이언트가 연결을 끊으면 서버가 반복 전송을 멈추고 구독을 정리하는지 검증한다.
     *
     * <p>서버는 다음 전송이 실패할 때 끊김을 알아채므로 정리는 비동기로 일어난다. 앞선 테스트가 남긴 구독이
     * 섞이지 않도록 먼저 구독 수가 0 이 될 때까지 기다린 뒤 시작한다.
     */
    @Test
    @DisplayName("클라이언트가 연결을 끊으면 구독이 정리된다")
    void cleansUpWhenClientDisconnects() throws Exception {
        long campaignId = saveCampaign().getCampaignId();
        awaitActiveSubscriptions(0);

        try (Stream<String> lines = openStream(campaignId)) {
            nextData(lines.iterator());
            assertThat(campaignStatsStreamService.activeSubscriptionCount()).isEqualTo(1);
        }

        awaitActiveSubscriptions(0);
    }

    /**
     * 구독 수가 기대한 값이 될 때까지 최대 10초 기다린 뒤, 그 값인지 확인한다.
     */
    private void awaitActiveSubscriptions(int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (campaignStatsStreamService.activeSubscriptionCount() != expected
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertThat(campaignStatsStreamService.activeSubscriptionCount()).isEqualTo(expected);
    }

    /** 소유 광고주의 access token 으로 연결하는 요청. */
    private HttpRequest request(long campaignId) {
        return request(campaignId, "Bearer " + jwtTokenProvider.createAccessToken(OWNER_ID));
    }

    /** Authorization 헤더 값을 직접 정해서 연결하는 요청. null 이면 헤더를 보내지 않는다. */
    private HttpRequest request(long campaignId, String authorization) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/campaigns/" + campaignId + "/stats/stream"))
                .header("Accept", "text/event-stream");
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return builder.GET().build();
    }

    private HttpResponse<String> getAsString(HttpRequest request) throws Exception {
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 스트림을 열고 응답 줄을 한 줄씩 읽을 수 있는 Stream 으로 돌려준다. 닫으면 연결도 끊긴다.
     */
    private Stream<String> openStream(long campaignId) throws Exception {
        HttpResponse<Stream<String>> response = HttpClient.newHttpClient().send(
                request(campaignId), HttpResponse.BodyHandlers.ofLines());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
        // 운영의 nginx 가 이벤트를 모아서 보내지 않도록 하는 헤더
        assertThat(response.headers().firstValue("X-Accel-Buffering")).hasValue("no");
        return response.body();
    }

    /**
     * 다음 'data:' 줄(이벤트 본문 JSON)이 나올 때까지 읽는다.
     */
    private String nextData(Iterator<String> lines) {
        while (lines.hasNext()) {
            String line = lines.next();
            if (line.startsWith("data:")) {
                return line.substring("data:".length());
            }
        }
        throw new AssertionError("스트림이 data 이벤트 없이 끝났습니다.");
    }

    private Campaign saveCampaign() {
        return campaignRepository.save(Campaign.builder()
                .userId(1L)
                .title("출근길 커피 1+1")
                .body("오전 9시 전 주문 시 1+1")
                .targetAgeGroup("20s")
                .status(CampaignStatus.ACTIVE)
                .build());
    }

    private void increaseSent(long campaignId, int times) {
        transactionTemplate.executeWithoutResult(status -> {
            for (int i = 0; i < times; i++) {
                campaignStatsRepository.increaseSentCount(campaignId, LocalDateTime.now(clock));
            }
        });
    }

    private void increaseClick(long campaignId, int times) {
        transactionTemplate.executeWithoutResult(status -> {
            for (int i = 0; i < times; i++) {
                campaignStatsRepository.increaseClickCount(campaignId, LocalDateTime.now(clock));
            }
        });
    }
}
