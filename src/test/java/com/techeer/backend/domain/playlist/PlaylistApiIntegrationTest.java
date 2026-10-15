package com.techeer.backend.domain.playlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.backend.domain.account.repository.MusicUserRepository;
import com.techeer.backend.domain.playlist.entity.ActiveListening;
import com.techeer.backend.domain.playlist.repository.ActiveListeningRepository;
import com.techeer.backend.domain.playlist.repository.MusicPlaylistRepository;
import com.techeer.backend.domain.playlist.service.PlaylistService;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties =
{
        "spring.datasource.url=jdbc:h2:mem:playlist_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlaylistApiIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MusicUserRepository users;

    @Autowired
    private MusicPlaylistRepository playlists;

    @Autowired
    private ActiveListeningRepository listeners;

    @Autowired
    private PlaylistService service;

    @BeforeEach
    void tracks() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS ranking");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ranking.track (
                    track_id varchar(128) PRIMARY KEY, title varchar(200) NOT NULL,
                    artist_name varchar(200) NOT NULL, preview_url varchar(1000), external_url varchar(1000))
                """);
        jdbc.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS playlist_track_unique_test
                ON public.music_playlist_tracks(playlist_id, track_id)
                """);
        jdbc.update("INSERT INTO ranking.track(track_id,title,artist_name) VALUES ('song-a','곡 A','가수 A')");
        jdbc.update("INSERT INTO ranking.track(track_id,title,artist_name) VALUES ('song-b','곡 B','가수 B')");
    }

    @AfterEach
    void cleanUp() {
        listeners.deleteAll();
        playlists.deleteAll();
        users.deleteAll();
        jdbc.update("DELETE FROM ranking.track");
    }

    @Test
    void shareAndCountDistinctActiveListenersInCurrentCell() throws Exception {
        String owner = register("owner@example.com");
        String listener1 = register("listener1@example.com");
        String listener2 = register("listener2@example.com");

        mvc.perform(post("/api/v1/playlists").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"퇴근 플리\",\"trackIds\":[\"song-a\",\"song-b\"]}"))
                .andExpect(status().isUnauthorized());

        JsonNode created = body(mvc.perform(post("/api/v1/playlists").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"퇴근 플리\",\"trackIds\":[\"song-a\",\"song-b\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long playlistId = created.path("data").path("playlistId").asLong();
        assertThat(created.path("data").path("tracks")).hasSize(2);

        mvc.perform(put("/api/v1/playlists/{id}/tracks", playlistId).header("Authorization", bearer(listener1))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"trackIds\":[\"song-a\"]}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/playlists/{id}/tracks", playlistId).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackIds\":[\"song-b\",\"song-a\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.tracks[0].id").value("song-b"));

        String shareCode = body(mvc.perform(post("/api/v1/playlists/{id}/share", playlistId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("data").path("shareCode").asText();
        mvc.perform(get("/api/v1/shared/playlists/{code}", shareCode))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("퇴근 플리"));
        mvc.perform(get("/api/v1/shared/playlists/{code}/active-listeners", shareCode)
                        .param("lat", "37.555").param("lng", "126.923")
                        .param("recordedAt", Instant.now().toString()))
                .andExpect(status().isUnauthorized());

        heartbeat(shareCode, listener1, 37.555, 126.923);
        heartbeat(shareCode, listener1, 37.555, 126.923);
        heartbeat(shareCode, listener2, 37.555, 126.923);
        assertCount(shareCode, owner, 37.555, 126.923, 2);

        heartbeat(shareCode, listener2, 37.566, 126.923);
        assertCount(shareCode, owner, 37.555, 126.923, 1);
        assertCount(shareCode, owner, 37.566, 126.923, 1);

        mvc.perform(delete("/api/v1/shared/playlists/{code}/listening", shareCode)
                        .header("Authorization", bearer(listener1))).andExpect(status().isNoContent());
        assertCount(shareCode, owner, 37.555, 126.923, 0);

        mvc.perform(delete("/api/v1/playlists/{id}/share", playlistId)
                        .header("Authorization", bearer(owner))).andExpect(status().isNoContent());
        assertThat(listeners.count()).isZero();
        mvc.perform(get("/api/v1/shared/playlists/{code}", shareCode)).andExpect(status().isNotFound());
    }

    @Test
    void staleLocationAndExpiredHeartbeatAreNotCounted() throws Exception {
        String owner = register("owner2@example.com");
        long playlistId = body(mvc.perform(post("/api/v1/playlists").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"출근 플리\",\"trackIds\":[\"song-a\"]}"))
                .andReturn().getResponse().getContentAsString()).path("data").path("playlistId").asLong();
        String code = body(mvc.perform(post("/api/v1/playlists/{id}/share", playlistId)
                        .header("Authorization", bearer(owner))).andReturn().getResponse().getContentAsString())
                .path("data").path("shareCode").asText();
        Instant stale = Instant.now().minusSeconds(31);
        mvc.perform(put("/api/v1/shared/playlists/{code}/listening", code)
                        .header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new Location(37.555, 126.923, stale))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/shared/playlists/{code}/active-listeners", code)
                        .header("Authorization", bearer(owner)).param("lat", "37.555")
                        .param("lng", "126.923").param("recordedAt", stale.toString()))
                .andExpect(status().isBadRequest());

        heartbeat(code, owner, 37.555, 126.923);
        assertCount(code, owner, 37.555, 126.923, 1);
        ActiveListening active = listeners.findById(users.findByEmailAndDeletedAtIsNull("owner2@example.com")
                .orElseThrow().getUserId()).orElseThrow();
        active.update(playlistId, active.getH3Cell(), Instant.now().minusSeconds(31), Instant.now());
        listeners.saveAndFlush(active);
        assertCount(code, owner, 37.555, 126.923, 0);
        assertThat(service.cleanupExpired()).isEqualTo(1);
        assertThat(listeners.count()).isZero();
    }

    @Test
    void emptyPlaylistCannotBeSharedAndOldLocationCannotUndoNewCell() throws Exception {
        String token = register("owner3@example.com");
        long playlistId = body(mvc.perform(post("/api/v1/playlists").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"초안\",\"trackIds\":[]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .path("data").path("playlistId").asLong();
        mvc.perform(post("/api/v1/playlists/{id}/share", playlistId)
                        .header("Authorization", bearer(token))).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/playlists/{id}/tracks", playlistId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackIds\":[\"song-a\",\"song-a\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/playlists/{id}/tracks", playlistId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackIds\":[\"song-a\"]}"))
                .andExpect(status().isOk());
        String code = body(mvc.perform(post("/api/v1/playlists/{id}/share", playlistId)
                        .header("Authorization", bearer(token))).andReturn().getResponse().getContentAsString())
                .path("data").path("shareCode").asText();
        Instant older = Instant.now().minusSeconds(4);
        heartbeat(code, token, 37.566, 126.923);
        mvc.perform(put("/api/v1/shared/playlists/{code}/listening", code)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new Location(37.555, 126.923, older))))
                .andExpect(status().isBadRequest());
        assertCount(code, token, 37.566, 126.923, 1);
        assertCount(code, token, 37.555, 126.923, 0);
    }

    private String register(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new Signup(email, "safe-password-123", "테스트 사용자"))))
                .andExpect(status().isCreated());
        String result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new Login(email, "safe-password-123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return body(result).path("data").path("accessToken").asText();
    }

    private void heartbeat(String code, String token, double lat, double lng) throws Exception {
        mvc.perform(put("/api/v1/shared/playlists/{code}/listening", code)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new Location(lat, lng, Instant.now()))))
                .andExpect(status().isOk());
    }

    private void assertCount(String code, String token, double lat, double lng, int expected) throws Exception {
        mvc.perform(get("/api/v1/shared/playlists/{code}/active-listeners", code)
                        .header("Authorization", bearer(token)).param("lat", Double.toString(lat))
                        .param("lng", Double.toString(lng)).param("recordedAt", Instant.now().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.activeListenerCount").value(expected));
    }

    private JsonNode body(String response) throws Exception {
        return mapper.readTree(response);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Signup(String email, String password, String displayName) {
    }

    private record Login(String email, String password) {
    }

    private record Location(double lat, double lng, Instant recordedAt) {
    }
}
