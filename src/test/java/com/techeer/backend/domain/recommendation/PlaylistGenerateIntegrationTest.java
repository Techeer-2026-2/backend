package com.techeer.backend.domain.recommendation;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
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
import com.techeer.backend.domain.playlist.entity.Track;
import com.techeer.backend.domain.playlist.entity.TrackMood;
import com.techeer.backend.domain.playlist.entity.TrackMoodScore;
import com.techeer.backend.domain.playlist.entity.TrackMoodSource;
import com.techeer.backend.domain.playlist.repository.PlaylistRepository;
import com.techeer.backend.domain.playlist.repository.TrackMoodScoreRepository;
import com.techeer.backend.domain.playlist.repository.TrackRepository;
import com.techeer.backend.domain.weather.WeatherCondition;
import com.techeer.backend.domain.weather.WeatherSnapshot;
import com.techeer.backend.domain.weather.service.WeatherService;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 요청 → 통근 프로필 → 날씨 → 후보 곡(DB) → 플레이리스트 저장 → 응답까지 실제 PostgreSQL 로 확인한다. 날씨만 가짜로 둔다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PlaylistGenerateIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CommuteProfileRepository profileRepository;
    @Autowired TrackRepository trackRepository;
    @Autowired TrackMoodScoreRepository moodScoreRepository;
    @Autowired PlaylistRepository playlistRepository;

    @MockitoBean WeatherService weatherService;

    /** 시간대 규칙이 테스트마다 달라지지 않도록 한국 시간 18시(퇴근 러시)로 고정한다. */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-16T09:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    private Long profileId;

    @BeforeEach
    void setUp() {
        playlistRepository.deleteAll();
        moodScoreRepository.deleteAll();
        trackRepository.deleteAll();
        profileRepository.deleteAll();

        for (long i = 1; i <= 20; i++) {
            Track gloomy = trackRepository.save(Track.builder().itunesTrackId(i).title("비 노래" + i)
                    .artist("가수" + i).genre("장르" + (i % 4)).trackTimeMillis(200_000).build());
            moodScoreRepository.save(TrackMoodScore.create(gloomy, TrackMood.GLOOMY, 0.9, TrackMoodSource.SCORED));
        }
        for (long i = 200; i < 205; i++) {
            Track calm = trackRepository.save(Track.builder().itunesTrackId(i).title("잔잔한 곡" + i)
                    .artist("잔잔" + i).genre("Acoustic").trackTimeMillis(200_000).build());
            moodScoreRepository.save(TrackMoodScore.create(calm, TrackMood.CALM, 0.9, TrackMoodSource.SCORED));
        }
        Track energetic = trackRepository.save(Track.builder().itunesTrackId(100L).title("신나는 곡")
                .artist("댄서").genre("Dance").trackTimeMillis(200_000).build());
        moodScoreRepository.save(TrackMoodScore.create(energetic, TrackMood.ENERGETIC, 1.0, TrackMoodSource.SCORED));

        profileId = profileRepository.save(CommuteProfile.builder()
                .memberId(1L).commuteType(CommuteType.FROM_WORK).frequentRouteName("강남-판교")
                .departure(new Location(37.5, 127.0, "강남")).arrival(new Location(37.4, 127.1, "판교"))
                .build()).getId();
        when(weatherService.getCurrentSnapshot(anyDouble(), anyDouble()))
                .thenReturn(new WeatherSnapshot(WeatherCondition.RAINY, 11.0, false, false));
    }

    private String body(long profile, Integer size) {
        return "{\"commuteProfileId\":" + profile + (size == null ? "" : ",\"size\":" + size) + "}";
    }

    @Test
    void 비_오는_날_요청하면_비에_어울리는_곡으로_COMMUTE_GENERATED_플레이리스트를_만든다() throws Exception {
        String json = mockMvc.perform(post("/api/v1/playlists/generate").header("X-Member-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(profileId, 8)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("COMMUTE_GENERATED"))
                .andExpect(jsonPath("$.tracks.length()").value(8))
                .andExpect(jsonPath("$.context.weather.condition").value("RAINY"))
                .andExpect(jsonPath("$.context.weather.temperatureCelsius").value(11))
                .andExpect(jsonPath("$.context.commuteType").value("FROM_WORK"))
                .andReturn().getResponse().getContentAsString();

        JsonNode root = objectMapper.readTree(json);
        // 비 오는 날 가중치에 없는 분위기(ENERGETIC) 곡은 한 곡도 들어가면 안 된다. (우울·잔잔 곡은 들어갈 수 있다)
        for (JsonNode track : root.get("tracks")) {
            org.assertj.core.api.Assertions.assertThat(track.get("trackName").asText()).isNotEqualTo("신나는 곡");
        }
        long playlistId = root.get("id").asLong();

        mockMvc.perform(get("/api/v1/playlists/" + playlistId).header("X-Member-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("COMMUTE_GENERATED"))
                .andExpect(jsonPath("$.tracks.length()").value(8));
        mockMvc.perform(get("/api/v1/playlists").header("X-Member-Id", "1"))
                .andExpect(jsonPath("$[0].type").value("COMMUTE_GENERATED"));
    }

    @Test
    void 날씨_호출이_실패해도_201_로_만들고_weather_는_null() throws Exception {
        when(weatherService.getCurrentSnapshot(anyDouble(), anyDouble()))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_ERROR));

        mockMvc.perform(post("/api/v1/playlists/generate").header("X-Member-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(profileId, 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.context.weather").doesNotExist())
                .andExpect(jsonPath("$.tracks.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void 남의_통근_프로필로는_404() throws Exception {
        mockMvc.perform(post("/api/v1/playlists/generate").header("X-Member-Id", "2")
                        .contentType(MediaType.APPLICATION_JSON).content(body(profileId, 5)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMUTE_PROFILE_NOT_FOUND"));
    }

    @Test
    void 곡_수가_범위를_벗어나거나_프로필이_없으면_400() throws Exception {
        mockMvc.perform(post("/api/v1/playlists/generate").header("X-Member-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(profileId, 0)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/playlists/generate").header("X-Member-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(profileId, 51)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/playlists/generate").header("X-Member-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 분위기_점수가_있는_곡이_하나도_없으면_404_PLAYLIST_GENERATE_NO_CANDIDATES() throws Exception {
        moodScoreRepository.deleteAll();

        mockMvc.perform(post("/api/v1/playlists/generate").header("X-Member-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(profileId, 5)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYLIST_GENERATE_NO_CANDIDATES"));
    }
}
