package com.techeer.backend.domain.playlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.playlist.entity.ActiveListening;
import com.techeer.backend.domain.playlist.entity.Playlist;
import com.techeer.backend.domain.playlist.entity.PlaylistTrack;
import com.techeer.backend.domain.playlist.entity.Track;
import com.techeer.backend.domain.playlist.repository.ActiveListeningRepository;
import com.techeer.backend.domain.playlist.repository.PlaylistRepository;
import com.techeer.backend.domain.playlist.repository.TrackRepository;
import com.techeer.backend.domain.playlist.service.PlaylistShareService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 공유 링크와 현재 셀 청취 인원을 실제 PostgreSQL(Testcontainers)로 검증한다.
 *
 * <p>회원당 한 행 유지(upsert)·동시 요청·만료 정리는 DB 의 제약과 SQL 에 기대므로 실제 DB 로 확인해야 의미가 있다.
 * 시나리오는 coder072 님이 올린 PR #83 의 통합 테스트를 현재 develop 구조에 맞춰 옮기고 넓혔다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PlaylistShareIntegrationTest {

    private static final double LAT = 37.555;
    private static final double LNG = 126.923;
    private static final double FAR_LAT = 37.60;
    private static final double FAR_LNG = 127.10;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private ActiveListeningRepository listeningRepository;

    @Autowired
    private PlaylistShareService shareService;

    private Track trackA;
    private Track trackB;

    @AfterEach
    void clean() {
        listeningRepository.deleteAll();
        playlistRepository.deleteAll();
        trackRepository.deleteAll();
    }

    @BeforeEach
    void setUp() {
        clean();
        trackA = trackRepository.save(Track.builder().itunesTrackId(1L).title("곡 A").artist("가수 A").build());
        trackB = trackRepository.save(Track.builder().itunesTrackId(2L).title("곡 B").artist("가수 B").build());
    }

    @Test
    @DisplayName("소유자가 공유하면 토큰을 받고, 다시 호출해도 같은 토큰이다")
    void shareIsIdempotent() throws Exception {
        long playlistId = playlist(1L, trackA, trackB);

        String first = shareToken(1L, playlistId);
        String second = shareToken(1L, playlistId);

        assertThat(first).isNotBlank().isEqualTo(second);
        mockMvc.perform(post("/api/v1/playlists/{id}/share-links", playlistId).header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiPath").value("/api/v1/shared/playlists/" + first));
    }

    @Test
    @DisplayName("같은 플레이리스트에 공유 요청이 동시에 와도 토큰은 하나다")
    void concurrentShareCreatesOneToken() throws Exception {
        long playlistId = playlist(1L, trackA);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return shareToken(1L, playlistId);
            }));
        }
        start.countDown();
        Set<String> tokens = new HashSet<>();
        for (Future<String> future : futures) {
            tokens.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        assertThat(tokens).hasSize(1);
    }

    @Test
    @DisplayName("남의 플레이리스트·없는 플레이리스트는 404, 곡이 없으면 409")
    void shareRejectsOthersAndEmpty() throws Exception {
        long playlistId = playlist(1L, trackA);
        long emptyId = playlist(1L);

        mockMvc.perform(post("/api/v1/playlists/{id}/share-links", playlistId).header("X-Member-Id", "2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYLIST_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/playlists/{id}/share-links", 999999).header("X-Member-Id", "1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/playlists/{id}/share-links", emptyId).header("X-Member-Id", "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAYLIST_EMPTY"));
        mockMvc.perform(delete("/api/v1/playlists/{id}/share-links", playlistId).header("X-Member-Id", "2"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("공유 조회는 로그인 없이 되고, 곡 순서대로 나오며, 소유자 ID 는 응답에 없다")
    void sharedViewNeedsNoLogin() throws Exception {
        long playlistId = playlist(1L, trackB, trackA);
        String token = shareToken(1L, playlistId);

        mockMvc.perform(get("/api/v1/shared/playlists/{t}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks.length()").value(2))
                .andExpect(jsonPath("$.tracks[0].trackName").value("곡 B"))
                .andExpect(jsonPath("$.tracks[1].trackName").value("곡 A"))
                .andExpect(jsonPath("$.memberId").doesNotExist());
        mockMvc.perform(get("/api/v1/shared/playlists/{t}", "no-such-token")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("공유를 취소하면 이전 토큰으로 조회할 수 없고 그 플레이리스트의 청취 기록이 지워진다")
    void revokeShareRemovesListening() throws Exception {
        long playlistId = playlist(1L, trackA);
        String token = shareToken(1L, playlistId);
        heartbeat(10L, token, LAT, LNG, Instant.now()).andExpect(status().isOk());
        assertThat(listeningRepository.count()).isEqualTo(1);

        mockMvc.perform(delete("/api/v1/playlists/{id}/share-links", playlistId).header("X-Member-Id", "1"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/shared/playlists/{t}", token)).andExpect(status().isNotFound());
        assertThat(listeningRepository.count()).isZero();
        String reissued = shareToken(1L, playlistId);
        assertThat(reissued).isNotEqualTo(token);
    }

    @Test
    @DisplayName("같은 셀에서 듣는 서로 다른 회원 수를 센다 (같은 회원의 반복 신호는 한 명)")
    void countsDistinctListenersInSameCell() throws Exception {
        String token = shareToken(1L, playlist(1L, trackA));

        heartbeat(10L, token, LAT, LNG, Instant.now()).andExpect(status().isOk())
                .andExpect(jsonPath("$.h3Cell").isNotEmpty());
        heartbeat(10L, token, LAT, LNG, Instant.now()).andExpect(status().isOk());
        heartbeat(11L, token, LAT, LNG, Instant.now()).andExpect(status().isOk());
        heartbeat(12L, token, FAR_LAT, FAR_LNG, Instant.now()).andExpect(status().isOk());

        activeListeners(20L, token, LAT, LNG).andExpect(status().isOk())
                .andExpect(jsonPath("$.activeListenerCount").value(2));
        activeListeners(20L, token, FAR_LAT, FAR_LNG).andExpect(jsonPath("$.activeListenerCount").value(1));
        assertThat(listeningRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("회원이 다른 셀·다른 플레이리스트로 옮기면 이전 쪽에서는 빠진다")
    void movingRemovesFromPrevious() throws Exception {
        String tokenOne = shareToken(1L, playlist(1L, trackA));
        String tokenTwo = shareToken(1L, playlist(1L, trackB));

        heartbeat(10L, tokenOne, LAT, LNG, Instant.now()).andExpect(status().isOk());
        heartbeat(10L, tokenOne, FAR_LAT, FAR_LNG, Instant.now()).andExpect(status().isOk());
        activeListeners(20L, tokenOne, LAT, LNG).andExpect(jsonPath("$.activeListenerCount").value(0));
        activeListeners(20L, tokenOne, FAR_LAT, FAR_LNG).andExpect(jsonPath("$.activeListenerCount").value(1));

        heartbeat(10L, tokenTwo, FAR_LAT, FAR_LNG, Instant.now()).andExpect(status().isOk());
        activeListeners(20L, tokenOne, FAR_LAT, FAR_LNG).andExpect(jsonPath("$.activeListenerCount").value(0));
        activeListeners(20L, tokenTwo, FAR_LAT, FAR_LNG).andExpect(jsonPath("$.activeListenerCount").value(1));
        assertThat(listeningRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 회원의 첫 신호가 동시에 여러 번 와도 실패하지 않고 한 행만 남는다")
    void concurrentFirstHeartbeatsKeepOneRow() throws Exception {
        String token = shareToken(1L, playlist(1L, trackA));
        // 같은 측정 시각으로 보낸다. 서로 다른 시각이면 늦게 도착한 오래된 신호가 설계대로 400 으로 거절된다.
        Instant recordedAt = Instant.now();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return heartbeat(10L, token, LAT, LNG, recordedAt).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        for (Future<Integer> future : futures) {
            assertThat(future.get(30, TimeUnit.SECONDS)).isEqualTo(200);
        }
        pool.shutdownNow();

        assertThat(listeningRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("오래됐거나 미래인 위치 시각, 이미 저장된 것보다 오래된 신호는 400 이고 저장을 바꾸지 않는다")
    void rejectsInvalidRecordedAt() throws Exception {
        String token = shareToken(1L, playlist(1L, trackA));
        Instant now = Instant.now();
        heartbeat(10L, token, LAT, LNG, now).andExpect(status().isOk());

        heartbeat(10L, token, FAR_LAT, FAR_LNG, now.minusSeconds(40)).andExpect(status().isBadRequest());
        heartbeat(10L, token, FAR_LAT, FAR_LNG, now.plusSeconds(60)).andExpect(status().isBadRequest());
        // 저장된 신호(now)보다 오래됐지만 30초 안인 늦게 도착한 신호: 최신 셀을 되돌리면 안 된다
        heartbeat(10L, token, FAR_LAT, FAR_LNG, now.minusSeconds(10)).andExpect(status().isBadRequest());

        activeListeners(20L, token, LAT, LNG).andExpect(jsonPath("$.activeListenerCount").value(1));
        mockMvc.perform(put("/api/v1/shared/playlists/{t}/listening", token).header("X-Member-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\":95,\"lng\":127,\"recordedAt\":\"" + now + "\"}"))
                .andExpect(status().isBadRequest());
        activeListeners(20L, token, LAT, LNG, now.minusSeconds(40)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("마지막 신호나 위치가 만료된 기록은 집계에서 빠지고 정리 작업이 지운다")
    void expiredListeningIsExcludedAndCleaned() throws Exception {
        String token = shareToken(1L, playlist(1L, trackA));
        Long playlistId = playlistRepository.findByShareToken(token).orElseThrow().getId();
        String cell = shareCell(token, LAT, LNG);
        Instant now = Instant.now();
        listeningRepository.save(new ActiveListening(10L, playlistId, cell, now, now));
        listeningRepository.save(new ActiveListening(11L, playlistId, cell, now, now.minusSeconds(120)));
        listeningRepository.save(new ActiveListening(12L, playlistId, cell, now.minusSeconds(120), now));

        activeListeners(20L, token, LAT, LNG).andExpect(jsonPath("$.activeListenerCount").value(1));
        int removed = shareService.cleanupExpired();

        assertThat(removed).isEqualTo(2);
        assertThat(listeningRepository.findAll()).extracting(ActiveListening::getMemberId).containsExactly(10L);
    }

    @Test
    @DisplayName("재생을 끝내면 빠지고, 반복해도 204 이며, 다른 플레이리스트로 옮긴 뒤 도착한 종료는 새 재생을 끊지 않는다")
    void stopIsIdempotentAndScoped() throws Exception {
        String tokenOne = shareToken(1L, playlist(1L, trackA));
        String tokenTwo = shareToken(1L, playlist(1L, trackB));
        heartbeat(10L, tokenOne, LAT, LNG, Instant.now()).andExpect(status().isOk());

        stop(10L, tokenOne).andExpect(status().isNoContent());
        stop(10L, tokenOne).andExpect(status().isNoContent());
        assertThat(listeningRepository.count()).isZero();

        heartbeat(10L, tokenTwo, LAT, LNG, Instant.now()).andExpect(status().isOk());
        stop(10L, tokenOne).andExpect(status().isNoContent());
        assertThat(listeningRepository.count()).isEqualTo(1);
        stop(10L, "no-such-token").andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("신호 전송·인원 조회는 회원 식별이 필요하고, 없는 토큰은 404")
    void listeningNeedsMember() throws Exception {
        String token = shareToken(1L, playlist(1L, trackA));
        String body = "{\"lat\":" + LAT + ",\"lng\":" + LNG + ",\"recordedAt\":\"" + Instant.now() + "\"}";

        mockMvc.perform(put("/api/v1/shared/playlists/{t}/listening", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_MEMBER_ID"));
        mockMvc.perform(get("/api/v1/shared/playlists/{t}/active-listeners", token)
                        .param("lat", String.valueOf(LAT)).param("lng", String.valueOf(LNG))
                        .param("recordedAt", Instant.now().toString()))
                .andExpect(status().isBadRequest());
        heartbeat(10L, "no-such-token", LAT, LNG, Instant.now()).andExpect(status().isNotFound());
        activeListeners(20L, "no-such-token", LAT, LNG).andExpect(status().isNotFound());
    }

    private long playlist(Long memberId, Track... tracks) {
        Playlist playlist = Playlist.builder().memberId(memberId).name("퇴근 플리").build();
        for (int i = 0; i < tracks.length; i++) {
            PlaylistTrack playlistTrack = PlaylistTrack.builder().playlist(playlist).track(tracks[i]).build();
            playlistTrack.updateOrder(i);
            playlist.getTracks().add(playlistTrack);
        }
        return playlistRepository.save(playlist).getId();
    }

    private String shareToken(Long memberId, long playlistId) throws Exception {
        String json = mockMvc.perform(post("/api/v1/playlists/{id}/share-links", playlistId)
                        .header("X-Member-Id", String.valueOf(memberId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("shareToken").asText();
    }

    private String shareCell(String token, double lat, double lng) throws Exception {
        String json = activeListeners(20L, token, lat, lng).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("h3Cell").asText();
    }

    private org.springframework.test.web.servlet.ResultActions heartbeat(
            Long memberId, String token, double lat, double lng, Instant recordedAt) throws Exception {
        return mockMvc.perform(put("/api/v1/shared/playlists/{t}/listening", token)
                .header("X-Member-Id", String.valueOf(memberId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(
                        Map.of("lat", lat, "lng", lng, "recordedAt", recordedAt.toString()))));
    }

    private org.springframework.test.web.servlet.ResultActions stop(Long memberId, String token) throws Exception {
        return mockMvc.perform(delete("/api/v1/shared/playlists/{t}/listening", token)
                .header("X-Member-Id", String.valueOf(memberId)));
    }

    private org.springframework.test.web.servlet.ResultActions activeListeners(
            Long memberId, String token, double lat, double lng) throws Exception {
        return activeListeners(memberId, token, lat, lng, Instant.now());
    }

    private org.springframework.test.web.servlet.ResultActions activeListeners(
            Long memberId, String token, double lat, double lng, Instant recordedAt) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/shared/playlists/{t}/active-listeners", token)
                .header("X-Member-Id", String.valueOf(memberId))
                .param("lat", String.valueOf(lat)).param("lng", String.valueOf(lng))
                .param("recordedAt", recordedAt.toString());
        return mockMvc.perform(request);
    }
}
