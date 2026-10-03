package com.techeer.backend.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.ranking.dto.RankingDtos.EventRequest;
import com.techeer.backend.domain.ranking.dto.RankingDtos.EventType;
import com.techeer.backend.domain.ranking.dto.RankingDtos.LocationInput;
import com.techeer.backend.domain.ranking.dto.RankingDtos.LocationRequest;
import com.techeer.backend.domain.ranking.dto.RankingDtos.StartRequest;
import com.techeer.backend.domain.ranking.dto.RankingDtos.TrackInput;
import com.techeer.backend.domain.ranking.service.PlaybackService;
import com.techeer.backend.domain.ranking.service.RankingBatchService;
import com.techeer.backend.domain.ranking.service.RankingCalculator;
import com.techeer.backend.domain.ranking.service.RankingGeo;
import com.techeer.backend.domain.ranking.service.RankingQueryService;
import com.techeer.backend.global.exception.BusinessException;
import com.uber.h3core.H3Core;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, RankingIntegrationTest.TestClock.class})
class RankingIntegrationTest {
    private static final Instant HOUR = Instant.parse("2026-10-03T02:00:00Z");
    private static final Instant NOW = HOUR.plusSeconds(3600);
    @Autowired private PlaybackService playback;
    @Autowired private RankingBatchService batch;
    @Autowired private RankingQueryService query;
    @Autowired private RankingGeo geo;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private TransactionTemplate transactions;
    @MockitoSpyBean private RankingCalculator calculator;

    @TestConfiguration
    static class TestClock {
        @Bean
        @Primary
        Clock rankingClock() {
            return Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
        }
    }

    @BeforeEach
    void resetData() {
        jdbc.execute("TRUNCATE ranking.playback_event, ranking.playback_session, ranking.latest_user_location, "
                + "ranking.hourly_track_rank, ranking.hourly_chart, ranking.batch, ranking.batch_attempt, "
                + "ranking.city_cell, ranking.track CASCADE");
        playback.saveTrack("A", new TrackInput("출근길", "테스트 아티스트", null, null));
        playback.saveTrack("B", new TrackInput("도시", "테스트 아티스트", null, null));
    }

    @ParameterizedTest
    @CsvSource({"30,100,VALID,NEARBY", "31,100,STALE,GLOBAL", "0,100.01,INACCURATE,GLOBAL"})
    void validatesLocationRelativeToStartNotCompletion(int age, double accuracy, String expected, String scope) {
        Instant start = HOUR.plusSeconds(100);
        for (int user = 1; user <= 20; user++) {
            StartRequest request = new StartRequest(UUID.randomUUID(), (long) user, "A", start,
                    new LocationInput(37.5442, 127.0561, accuracy, start.minusSeconds(age)));
            assertThat(playback.start(request).locationStatus()).isEqualTo(expected);
            playback.record(event(request.sessionId(), start.plusSeconds(180), 0.9));
        }
        batch.rebuild(HOUR);
        assertThat(query.nearby(37.5442, 127.0561, 20).scope()).isEqualTo(scope);
        assertThat(query.global(20).items().getFirst().uniqueListenerCount()).isEqualTo(20);
    }

    @Test
    void movementAfterStartDoesNotMoveTheVote() {
        Instant start = HOUR.plusSeconds(100);
        for (int user = 1; user <= 20; user++) {
            playback.updateLocation(new LocationRequest((long) user, location(start)));
            UUID session = UUID.randomUUID();
            playback.start(new StartRequest(session, (long) user, "A", start, null));
            playback.updateLocation(new LocationRequest((long) user,
                    new LocationInput(35.1796, 129.0756, 10.0, start.plusSeconds(120))));
            playback.record(event(session, start.plusSeconds(180), 1));
        }
        batch.rebuild(HOUR);
        assertThat(query.nearby(37.5442, 127.0561, 20).scope()).isEqualTo("NEARBY");
        assertThat(query.nearby(35.1796, 129.0756, 20).scope()).isEqualTo("GLOBAL");
    }

    @Test
    void keepsLatestLocationWhenAnOlderUpdateArrives() {
        Instant start = HOUR.plusSeconds(100);
        playback.updateLocation(new LocationRequest(1L, location(start)));
        playback.updateLocation(new LocationRequest(1L, new LocationInput(35.0, 129.0, 10.0, start.minusSeconds(60))));
        var result = playback.start(new StartRequest(UUID.randomUUID(), 1L, "A", start, null));
        assertThat(result.locationStatus()).isEqualTo("VALID");
        assertThat(jdbc.queryForObject("SELECT h3_cell FROM ranking.playback_session", String.class))
                .isEqualTo(geo.cell(37.5442, 127.0561));
    }

    @Test
    void rejectsInvalidAndFutureLocationAndFutureCompletion() throws Exception {
        var request = new StartRequest(UUID.randomUUID(), 1L, "A", HOUR,
                new LocationInput(91.0, 127.0, 10.0, HOUR));
        mvc.perform(post("/api/v1/playback/sessions").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(request))).andExpect(status().isBadRequest());
        assertThatThrownBy(() -> playback.start(new StartRequest(UUID.randomUUID(), 1L, "A", HOUR,
                location(HOUR.plusSeconds(1))))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> playback.start(new StartRequest(UUID.randomUUID(), 1L, "A", NOW.plusSeconds(1), null)))
                .isInstanceOf(BusinessException.class);
        UUID session = start(1, "A", HOUR, null);
        assertThatThrownBy(() -> playback.record(event(session, NOW.plusSeconds(1), 1))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> playback.record(event(session, HOUR.minusSeconds(1), 1))).isInstanceOf(BusinessException.class);
    }

    @Test
    void eventAndSessionRetriesAreIdempotentAndConflictsReturn409() throws Exception {
        StartRequest request = new StartRequest(UUID.randomUUID(), 1L, "A", HOUR, location(HOUR));
        assertThat(playback.start(request).created()).isTrue();
        assertThat(playback.start(request).created()).isFalse();
        EventRequest completion = event(request.sessionId(), HOUR.plusSeconds(180), 1);
        mvc.perform(post("/api/v1/playback/events").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(completion))).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/playback/events").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(completion))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/playback/events").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(event(request.sessionId(), HOUR.plusSeconds(180), 1))))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ranking.playback_event", Long.class)).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateEventsOnlyCreateOneRow() throws Exception {
        EventRequest event = event(start(1, "A", HOUR, null), HOUR.plusSeconds(100), 1);
        CountDownLatch signal = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> {
                    signal.await();
                    return playback.record(event).created();
                }));
            }
            signal.countDown();
            int created = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(15, TimeUnit.SECONDS)) {
                    created++;
                }
            }
            assertThat(created).isEqualTo(1);
        }
    }

    @Test
    void repeatPlaysAndRepeatedBatchesDoNotInflateUniqueScore() {
        audience(20, HOUR, location(HOUR));
        complete(1, "A", HOUR.plusSeconds(1000), location(HOUR.plusSeconds(1000)));
        batch.rebuild(HOUR);
        batch.rebuild(HOUR);
        assertThat(query.global(20).items().getFirst().uniqueListenerCount()).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT completion_count FROM ranking.hourly_track_rank WHERE scope_key='GLOBAL'", Long.class))
                .isEqualTo(21);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ranking.batch", Long.class)).isEqualTo(1);
    }

    @Test
    void hoursAreIndependentAndCompletionBoundaryIsExclusive() {
        audience(20, HOUR.minusSeconds(3600), null);
        batch.rebuild(HOUR.minusSeconds(3600));
        UUID session = start(1, "A", HOUR.minusSeconds(20), null);
        playback.record(event(session, HOUR, 1));
        for (int user = 2; user <= 20; user++) {
            complete(user, "A", HOUR, null);
        }
        batch.rebuild(HOUR);
        assertThat(query.global(20).items().getFirst().uniqueListenerCount()).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT event_count FROM ranking.batch WHERE ranked_hour = ?", Long.class,
                Timestamp.from(HOUR.minusSeconds(3600)))).isEqualTo(20);
        assertThat(query.global(20).rankedHour().toString()).isEqualTo("2026-10-03T11:00+09:00");
    }

    @Test
    void belowNinetyPercentAndSkippedDoNotCount() {
        for (int user = 1; user <= 20; user++) {
            UUID session = start(user, "A", HOUR, location(HOUR));
            playback.record(event(session, HOUR.plusSeconds(180), 0.8999));
            UUID skipped = start(user, "A", HOUR, location(HOUR));
            playback.record(new EventRequest(UUID.randomUUID(), skipped, EventType.TRACK_SKIPPED, 1.0, HOUR.plusSeconds(200)));
        }
        batch.rebuild(HOUR);
        assertThat(query.global(20).items()).isEmpty();
    }

    @Test
    void sparseRegionsAndTracksAreHidden() {
        audience(19, HOUR, location(HOUR));
        batch.rebuild(HOUR);
        assertThat(query.nearby(37.5442, 127.0561, 20).items()).isEmpty();
        complete(20, "A", HOUR, location(HOUR));
        complete(1, "B", HOUR, location(HOUR));
        complete(2, "B", HOUR, location(HOUR));
        batch.rebuild(HOUR);
        assertThat(query.global(20).items()).hasSize(1);
    }

    @Test
    void parentFallbackUsesSavedParentChart() throws Exception {
        String queryCell = geo.cell(37.5442, 127.0561);
        H3Core h3 = H3Core.newInstance();
        String distantSibling = h3.cellToChildren(geo.parent(queryCell), 8).stream()
                .filter(cell -> !geo.neighbors(queryCell).contains(cell)).findFirst().orElseThrow();
        var center = h3.cellToLatLng(distantSibling);
        audience(20, HOUR, new LocationInput(center.lat, center.lng, 10.0, HOUR));
        batch.rebuild(HOUR);
        assertThat(query.nearby(37.5442, 127.0561, 20).scope()).isEqualTo("PARENT");
    }

    @Test
    void cityFallbackUsesAuthoritativeCellMapThenGlobalForUnmappedLocation() {
        String origin = geo.cell(37.5442, 127.0561);
        String other = geo.cell(37.5665, 126.9780);
        jdbc.update("INSERT INTO ranking.city_cell VALUES (?, 'seoul-test', '서울 테스트'), (?, 'seoul-test', '서울 테스트')",
                origin, other);
        audience(20, HOUR, new LocationInput(37.5665, 126.9780, 10.0, HOUR));
        batch.rebuild(HOUR);
        var response = query.nearby(37.5442, 127.0561, 20);
        assertThat(response.scope()).isEqualTo("CITY");
        assertThat(response.fallbackApplied()).isTrue();
        assertThat(response.locationLabel()).isEqualTo("서울 테스트 인기곡");
        assertThat(query.nearby(35.1796, 129.0756, 20).scope()).isEqualTo("GLOBAL");
    }

    @Test
    void failedRerunPreservesPublishedChart() {
        audience(20, HOUR, null);
        batch.rebuild(HOUR);
        doThrow(new IllegalStateException("test failure")).when(calculator).calculate(anyList());
        assertThatThrownBy(() -> batch.rebuild(HOUR)).isInstanceOf(IllegalStateException.class);
        assertThat(query.global(20).items().getFirst().uniqueListenerCount()).isEqualTo(20);
    }

    @Test
    void databaseFailureAfterDeletingOldRowsRollsBackEntirePublication() {
        audience(20, HOUR, null);
        batch.rebuild(HOUR);
        jdbc.execute("""
                CREATE FUNCTION ranking.fail_chart_write() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'injected chart write failure'; END; $$
                """);
        jdbc.execute("CREATE TRIGGER fail_chart_write BEFORE INSERT ON ranking.hourly_chart "
                + "FOR EACH ROW EXECUTE FUNCTION ranking.fail_chart_write()");
        try {
            assertThatThrownBy(() -> batch.rebuild(HOUR)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(query.global(20).items().getFirst().uniqueListenerCount()).isEqualTo(20);
        } finally {
            jdbc.execute("DROP TRIGGER fail_chart_write ON ranking.hourly_chart");
            jdbc.execute("DROP FUNCTION ranking.fail_chart_write()");
        }
    }

    @Test
    void simultaneousBatchLockSkipsSameHour() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> holder = executor.submit(() -> transactions.executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(763142, ?)", Boolean.class,
                        Math.toIntExact(HOUR.getEpochSecond() / 3600));
                locked.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(batch.rebuild(HOUR)).isFalse();
            } finally {
                release.countDown();
            }
            holder.get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void apiReturnsOnlySavedSnapshotAndValidatesQuery() throws Exception {
        mvc.perform(get("/rankings/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/rankings/index.html"));
        mvc.perform(get("/rankings/index.html")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/rankings/tracks/global")).andExpect(jsonPath("$.data.status").value("PENDING"));
        audience(20, HOUR, null);
        batch.rebuild(HOUR);
        complete(21, "A", HOUR, null);
        mvc.perform(get("/api/v1/rankings/tracks/nearby?lat=37.5442&lng=127.0561&limit=1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].uniqueListenerCount").value(20))
                .andExpect(jsonPath("$.data.fallbackApplied").value(true));
        mvc.perform(get("/api/v1/rankings/tracks/global?limit=21")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/rankings/tracks/nearby?lat=NaN&lng=127")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/rankings/tracks/nearby?lat=37")).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnfinishedHourAndRebuildCanRemoveOldItems() {
        assertThatThrownBy(() -> batch.rebuild(NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> batch.rebuild(HOUR.plusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        audience(20, HOUR, null);
        batch.rebuild(HOUR);
        jdbc.update("DELETE FROM ranking.playback_event");
        batch.rebuild(HOUR);
        assertThat(query.global(20).items()).isEmpty();
        assertThat(query.global(20).status()).isEqualTo("INSUFFICIENT_DATA");
    }

    private void audience(int count, Instant start, LocationInput location) {
        for (int user = 1; user <= count; user++) {
            complete(user, "A", start, location);
        }
    }

    private void complete(long user, String track, Instant start, LocationInput location) {
        playback.record(event(start(user, track, start, location), start.plusSeconds(180), 1));
    }

    private UUID start(long user, String track, Instant at, LocationInput location) {
        UUID id = UUID.randomUUID();
        playback.start(new StartRequest(id, user, track, at, location));
        return id;
    }

    private LocationInput location(Instant at) {
        return new LocationInput(37.5442, 127.0561, 10.0, at);
    }

    private EventRequest event(UUID session, Instant at, double ratio) {
        return new EventRequest(UUID.randomUUID(), session, EventType.TRACK_COMPLETED, ratio, at);
    }
}
