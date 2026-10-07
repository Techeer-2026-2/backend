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
import org.springframework.test.context.ActiveProfiles;

/**
 * 연결 유지 시간(timeout)이 지났을 때의 동작을 검증한다. 운영 기본값은 30분이라 테스트에서는 0.7초로 줄인다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"stats.stream.interval-ms=200", "stats.stream.timeout-ms=700"})
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class CampaignStatsStreamTimeoutIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private CampaignStatsRepository campaignStatsRepository;

    @Autowired
    private CampaignStatsStreamService campaignStatsStreamService;

    @AfterEach
    void tearDown() {
        campaignStatsRepository.deleteAll();
        campaignRepository.deleteAll();
    }

    /**
     * timeout 이 지나면 서버가 응답을 정상적으로 끝내고(클라이언트가 스트림 끝을 읽음) 구독이 정리되며,
     * 클라이언트가 다시 연결하면 곧바로 현재 값을 받는지 검증한다. (EventSource 의 자동 재연결과 같은 동작)
     */
    @Test
    @DisplayName("timeout 이 지나면 연결이 끝나고 구독이 정리되며, 다시 연결하면 현재 값을 바로 받는다")
    void closesOnTimeoutAndReconnectWorks() throws Exception {
        long campaignId = campaignRepository.save(Campaign.builder()
                .userId(1L).title("제목").targetAgeGroup("20s").status(CampaignStatus.ACTIVE).build()).getCampaignId();
        awaitActive(0);

        int dataEvents = 0;
        long start = System.currentTimeMillis();
        try (Stream<String> lines = open(campaignId)) {
            Iterator<String> it = lines.iterator();
            while (it.hasNext()) {          // 서버가 연결을 닫으면 hasNext() 가 false 가 되어 반복이 끝난다
                if (it.next().startsWith("data:")) {
                    dataEvents++;
                }
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("PROBE 스트림이 끝남: " + elapsed + "ms 후, 받은 data 이벤트 " + dataEvents + "개");

        assertThat(elapsed).isBetween(500L, 5_000L);
        assertThat(dataEvents).isGreaterThanOrEqualTo(1);
        awaitActive(0);
        System.out.println("PROBE 타임아웃 후 구독 수 = " + campaignStatsStreamService.activeSubscriptionCount());

        // 재연결: 새 연결이 곧바로 현재 값을 보낸다
        try (Stream<String> again = open(campaignId)) {
            Iterator<String> it = again.iterator();
            String first = null;
            while (it.hasNext() && first == null) {
                String line = it.next();
                if (line.startsWith("data:")) {
                    first = line;
                }
            }
            assertThat(first).contains("\"campaignId\":" + campaignId);
        }
    }

    private Stream<String> open(long campaignId) throws Exception {
        HttpResponse<Stream<String>> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/campaigns/" + campaignId
                        + "/stats/stream")).header("Accept", "text/event-stream")
                        .header("Authorization", "Bearer " + jwtTokenProvider.createAccessToken(1L))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofLines());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private void awaitActive(int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (campaignStatsStreamService.activeSubscriptionCount() != expected
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertThat(campaignStatsStreamService.activeSubscriptionCount()).isEqualTo(expected);
    }
}
