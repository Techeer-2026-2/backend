package com.techeer.backend.domain.commutesession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.commuteprofile.entity.CommuteProfile;
import com.techeer.backend.domain.commuteprofile.entity.CommuteType;
import com.techeer.backend.domain.commuteprofile.entity.Location;
import com.techeer.backend.domain.commuteprofile.repository.CommuteProfileRepository;
import com.techeer.backend.domain.commuteprofile.repository.CommuteRouteRepository;
import com.techeer.backend.domain.playlist.entity.Playlist;
import com.techeer.backend.domain.playlist.entity.Track;
import com.techeer.backend.domain.playlist.repository.PlaylistRepository;
import com.techeer.backend.domain.playlist.repository.TrackRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 통근 세션을 실제 PostgreSQL(Testcontainers)로 검증한다.
 *
 * <p>회원당 활성 세션 1개·이벤트 중복 방지·종료/취소 경합은 DB 제약과 조건부 SQL 에 기대므로 실제 DB 로 확인해야 의미가 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CommuteSessionIntegrationTest {

    private static final String SESSIONS = "/api/v1/commute-sessions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CommuteProfileRepository profileRepository;

    @Autowired
    private CommuteRouteRepository routeRepository;

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private TrackRepository trackRepository;

    private long profileId;
    private long playlistId;

    @AfterEach
    void clean() {
        jdbcTemplate.update("delete from commute_session_events");
        jdbcTemplate.update("delete from commute_sessions");
        playlistRepository.deleteAll();
        trackRepository.deleteAll();
        routeRepository.deleteAll();
        profileRepository.deleteAll();
    }

    @BeforeEach
    void setUp() {
        clean();
        profileId = profile(1L, 32);
        playlistId = playlistRepository.save(Playlist.builder().memberId(1L).name("출근길").build()).getId();
        trackRepository.save(Track.builder().itunesTrackId(100L).title("곡 A").artist("가수 A")
                .artworkUrl("http://img/a").build());
        trackRepository.save(Track.builder().itunesTrackId(200L).title("곡 B").artist("가수 B").build());
    }

    @Test
    @DisplayName("통근을 시작하면 201 과 함께 프로필 경로의 예상 소요시간·도착 예정 시각을 돌려준다")
    void startReturnsExpectedDuration() throws Exception {
        start(1L, profileId, playlistId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.commuteProfileId").value(profileId))
                .andExpect(jsonPath("$.playlistId").value(playlistId))
                .andExpect(jsonPath("$.expectedDurationMinutes").value(32))
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.expectedArrivalAt").isNotEmpty())
                .andExpect(jsonPath("$.endedAt").doesNotExist());
    }

    @Test
    @DisplayName("플레이리스트 없이, 경로 없는 프로필로도 시작할 수 있고 예상 소요시간은 null")
    void startWithoutPlaylistOrRoute() throws Exception {
        long noRoute = profileWithoutRoute(1L);

        start(1L, noRoute, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.playlistId").doesNotExist())
                .andExpect(jsonPath("$.expectedDurationMinutes").doesNotExist())
                .andExpect(jsonPath("$.expectedArrivalAt").doesNotExist());
    }

    @Test
    @DisplayName("남의 프로필·남의 플레이리스트·없는 프로필은 404, 프로필 ID 없으면 400")
    void startRejectsOthersAndInvalid() throws Exception {
        start(2L, profileId, null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMUTE_PROFILE_NOT_FOUND"));
        start(1L, 999999L, null).andExpect(status().isNotFound());
        long other = profile(2L, 20);
        start(2L, other, playlistId).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYLIST_NOT_FOUND"));
        mockMvc.perform(post(SESSIONS).header("X-Member-Id", "1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(count("commute_sessions")).isZero();
    }

    @Test
    @DisplayName("진행 중인 통근이 있으면 다시 시작할 수 없고(409), 끝내거나 취소하면 다시 시작할 수 있다")
    void onlyOneActiveSessionPerMember() throws Exception {
        long first = sessionId(start(1L, profileId, null).andExpect(status().isCreated()));

        start(1L, profileId, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMUTE_SESSION_ALREADY_ACTIVE"));

        postAs(1L, SESSIONS + "/" + first + "/cancel").andExpect(status().isOk());
        long second = sessionId(start(1L, profileId, null).andExpect(status().isCreated()));
        postAs(1L, SESSIONS + "/" + second + "/complete").andExpect(status().isOk());
        start(1L, profileId, null).andExpect(status().isCreated());
        assertThat(count("commute_sessions")).isEqualTo(3);
    }

    @Test
    @DisplayName("같은 회원이 동시에 여러 번 시작해도 진행 중인 통근은 하나만 생기고 나머지는 409")
    void concurrentStartCreatesOneSession() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                return start(1L, profileId, null).andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(count("commute_sessions where status = 'ACTIVE'")).isEqualTo(1);
    }

    @Test
    @DisplayName("현재 통근 조회: 진행 중이면 200, 없거나 끝났으면 404, 다른 회원 것은 보이지 않는다")
    void activeLookup() throws Exception {
        getAs(1L, SESSIONS + "/active").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMUTE_SESSION_NOT_FOUND"));
        long id = sessionId(start(1L, profileId, null));

        getAs(1L, SESSIONS + "/active").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        getAs(2L, SESSIONS + "/active").andExpect(status().isNotFound());
        postAs(1L, SESSIONS + "/" + id + "/complete").andExpect(status().isOk());
        getAs(1L, SESSIONS + "/active").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("재생 이벤트를 기록하면 201, 같은 이벤트를 다시 보내면 200 이고 한 번만 저장된다")
    void eventIsIdempotent() throws Exception {
        long id = sessionId(start(1L, profileId, null));
        UUID eventId = UUID.randomUUID();

        event(1L, id, eventId, 100L, "PLAY", 120).andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(true));
        event(1L, id, eventId, 100L, "PLAY", 120).andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false));

        assertThat(count("commute_session_events")).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 eventId 에 내용이 다르거나 다른 통근의 eventId 를 쓰면 409")
    void eventConflicts() throws Exception {
        long id = sessionId(start(1L, profileId, null));
        UUID eventId = UUID.randomUUID();
        event(1L, id, eventId, 100L, "PLAY", 120).andExpect(status().isCreated());

        event(1L, id, eventId, 200L, "PLAY", 120).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMUTE_SESSION_EVENT_CONFLICT"));
        event(1L, id, eventId, 100L, "SKIP", 120).andExpect(status().isConflict());
        event(1L, id, eventId, 100L, "PLAY", 121).andExpect(status().isConflict());

        // 다른 회원이 같은 eventId 를 쓰면 자기 통근에서도 거절된다
        long other = profile(2L, 10);
        long otherSession = sessionId(start(2L, other, null));
        event(2L, otherSession, eventId, 100L, "PLAY", 120).andExpect(status().isConflict());
        assertThat(count("commute_session_events")).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 이벤트가 동시에 여러 번 와도 한 번만 기록된다")
    void concurrentSameEventKeepsOneRow() throws Exception {
        long id = sessionId(start(1L, profileId, null));
        UUID eventId = UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                return event(1L, id, eventId, 100L, "PLAY", 30).andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        assertThat(statuses).containsOnly(200, 201);
        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(count("commute_session_events")).isEqualTo(1);
    }

    @Test
    @DisplayName("이벤트 값이 잘못되면 400, 남의 통근·끝난 통근이면 404·409")
    void eventValidationAndOwnership() throws Exception {
        long id = sessionId(start(1L, profileId, null));

        mockMvc.perform(post(SESSIONS + "/" + id + "/playback-events").header("X-Member-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"" + UUID.randomUUID() + "\",\"itunesTrackId\":100,\"action\":\"DANCE\"}"))
                .andExpect(status().isBadRequest());
        event(1L, id, UUID.randomUUID(), -1L, "PLAY", 10).andExpect(status().isBadRequest());
        event(1L, id, UUID.randomUUID(), 100L, "PLAY", -5).andExpect(status().isBadRequest());
        event(2L, id, UUID.randomUUID(), 100L, "PLAY", 10).andExpect(status().isNotFound());

        postAs(1L, SESSIONS + "/" + id + "/complete").andExpect(status().isOk());
        event(1L, id, UUID.randomUUID(), 100L, "PLAY", 10).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMUTE_SESSION_NOT_ACTIVE"));
        assertThat(count("commute_session_events")).isZero();
    }

    @Test
    @DisplayName("종료하면 곡·시간 요약을 돌려준다 (재생 곡 수, 곡별 횟수·들은 시간, 건너뜀, 실제 통근 시간)")
    void completeReturnsSummary() throws Exception {
        long id = sessionId(start(1L, profileId, null));
        jdbcTemplate.update("update commute_sessions set started_at = started_at - interval '32 minutes'");
        event(1L, id, UUID.randomUUID(), 100L, "PLAY", 180).andExpect(status().isCreated());
        event(1L, id, UUID.randomUUID(), 100L, "REPEAT", 60).andExpect(status().isCreated());
        event(1L, id, UUID.randomUUID(), 200L, "PLAY", 200).andExpect(status().isCreated());
        event(1L, id, UUID.randomUUID(), 300L, "SKIP", 5).andExpect(status().isCreated());   // 우리 곡 목록에 없는 곡
        event(1L, id, UUID.randomUUID(), 300L, "SKIP", null).andExpect(status().isCreated());

        String json = postAs(1L, SESSIONS + "/" + id + "/complete").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.endedAt").isNotEmpty())
                .andExpect(jsonPath("$.playedTrackCount").value(2))
                .andExpect(jsonPath("$.totalPlayedSeconds").value(445))
                .andExpect(jsonPath("$.skipCount").value(2))
                .andExpect(jsonPath("$.tracks.length()").value(3))
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(json);
        assertThat(root.get("commuteSeconds").asLong()).isBetween(32 * 60L, 32 * 60L + 30);
        JsonNode first = root.get("tracks").get(0);   // 들은 시간이 긴 순서: 200(200초), 100(240초)... 합산 후 100 이 1등
        assertThat(first.get("itunesTrackId").asLong()).isEqualTo(100L);
        assertThat(first.get("trackName").asText()).isEqualTo("곡 A");
        assertThat(first.get("artworkUrl").asText()).isEqualTo("http://img/a");
        assertThat(first.get("playCount").asInt()).isEqualTo(1);
        assertThat(first.get("repeatCount").asInt()).isEqualTo(1);
        assertThat(first.get("playedSeconds").asLong()).isEqualTo(240);
        JsonNode unknown = root.get("tracks").get(2);
        assertThat(unknown.get("itunesTrackId").asLong()).isEqualTo(300L);
        // 이 앱은 값이 null 인 필드를 응답에서 뺀다. 곡 정보를 모르는 곡은 이름이 없다.
        assertThat(unknown.path("trackName").asText(null)).isNull();
        assertThat(unknown.get("skipCount").asInt()).isEqualTo(2);

        getAs(1L, SESSIONS + "/" + id + "/summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.playedTrackCount").value(2));
    }

    @Test
    @DisplayName("진행 중에도 지금까지의 요약을 볼 수 있고, 남의 통근 요약은 404")
    void summaryWhileActive() throws Exception {
        long id = sessionId(start(1L, profileId, null));
        event(1L, id, UUID.randomUUID(), 100L, "PLAY", 50).andExpect(status().isCreated());

        getAs(1L, SESSIONS + "/" + id + "/summary").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.endedAt").doesNotExist())
                .andExpect(jsonPath("$.totalPlayedSeconds").value(50));
        getAs(2L, SESSIONS + "/" + id + "/summary").andExpect(status().isNotFound());
        getAs(1L, SESSIONS + "/999999/summary").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("취소하면 CANCELED 가 되고, 이미 끝난 통근을 다시 종료·취소하면 409, 남의 통근은 404")
    void cancelAndRepeatedFinish() throws Exception {
        long id = sessionId(start(1L, profileId, null));

        postAs(2L, SESSIONS + "/" + id + "/cancel").andExpect(status().isNotFound());
        postAs(2L, SESSIONS + "/" + id + "/complete").andExpect(status().isNotFound());
        postAs(1L, SESSIONS + "/" + id + "/cancel").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.endedAt").isNotEmpty());
        postAs(1L, SESSIONS + "/" + id + "/cancel").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMUTE_SESSION_NOT_ACTIVE"));
        postAs(1L, SESSIONS + "/" + id + "/complete").andExpect(status().isConflict());
        assertThat(count("commute_sessions where status = 'CANCELED'")).isEqualTo(1);
    }

    @Test
    @DisplayName("종료와 취소가 동시에 와도 하나만 성공한다")
    void completeAndCancelRace() throws Exception {
        long id = sessionId(start(1L, profileId, null));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<Integer> complete = pool.submit(() -> {
            go.await();
            return postAs(1L, SESSIONS + "/" + id + "/complete").andReturn().getResponse().getStatus();
        });
        Future<Integer> cancel = pool.submit(() -> {
            go.await();
            return postAs(1L, SESSIONS + "/" + id + "/cancel").andReturn().getResponse().getStatus();
        });
        go.countDown();
        List<Integer> statuses = List.of(complete.get(30, TimeUnit.SECONDS), cancel.get(30, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(count("commute_sessions where status = 'ACTIVE'")).isZero();
    }

    @Test
    @DisplayName("회원 식별이 없으면 400")
    void needsMember() throws Exception {
        mockMvc.perform(get(SESSIONS + "/active")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_MEMBER_ID"));
    }

    private long profile(Long memberId, int minutes) {
        CommuteProfile profile = profileWithRoute(memberId);
        // 경로는 SQL 로 직접 넣는다. CommuteRoute 엔티티를 저장하면 route_detail(jsonb) 칼럼에 문자열을 넣어 PostgreSQL 이
        // 거절하는 기존 문제가 있다(통근 프로필 API 쪽 별도 이슈). 이 테스트가 확인하려는 것은 통근 세션이다.
        jdbcTemplate.update("""
                insert into commute_routes (commute_profile_id, transport_mode, average_duration_minutes,
                                            created_at, updated_at)
                values (?, 'PUBLIC_TRANSIT', ?, now(), now())
                """, profile.getId(), minutes);
        return profile.getId();
    }

    private long profileWithoutRoute(Long memberId) {
        return profileWithRoute(memberId).getId();
    }

    private CommuteProfile profileWithRoute(Long memberId) {
        return profileRepository.save(CommuteProfile.builder()
                .memberId(memberId).commuteType(CommuteType.TO_WORK).frequentRouteName("강남-판교")
                .departure(new Location(37.5, 127.0, "강남")).arrival(new Location(37.4, 127.1, "판교")).build());
    }

    private ResultActions start(Long memberId, Long commuteProfileId, Long playlist) throws Exception {
        Map<String, Object> body = playlist == null
                ? Map.of("commuteProfileId", commuteProfileId)
                : Map.of("commuteProfileId", commuteProfileId, "playlistId", playlist);
        return mockMvc.perform(post(SESSIONS).header("X-Member-Id", String.valueOf(memberId))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body)));
    }

    private ResultActions event(Long memberId, long sessionId, UUID eventId, Long itunesTrackId, String action,
            Integer playedSeconds) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("eventId", eventId.toString());
        body.put("itunesTrackId", itunesTrackId);
        body.put("action", action);
        if (playedSeconds != null) {
            body.put("playedSeconds", playedSeconds);
        }
        return mockMvc.perform(post(SESSIONS + "/" + sessionId + "/playback-events")
                .header("X-Member-Id", String.valueOf(memberId))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body)));
    }

    private ResultActions postAs(Long memberId, String url) throws Exception {
        return mockMvc.perform(post(url).header("X-Member-Id", String.valueOf(memberId)));
    }

    private ResultActions getAs(Long memberId, String url) throws Exception {
        return mockMvc.perform(get(url).header("X-Member-Id", String.valueOf(memberId)));
    }

    private long sessionId(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long count(String tableAndWhere) {
        Long n = jdbcTemplate.queryForObject("select count(*) from " + tableAndWhere, Long.class);
        return n == null ? 0 : n;
    }
}
