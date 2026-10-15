package com.techeer.backend.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.techeer.backend.domain.commuteprofile.entity.CommuteProfile;
import com.techeer.backend.domain.commuteprofile.entity.CommuteType;
import com.techeer.backend.domain.commuteprofile.entity.Location;
import com.techeer.backend.domain.commuteprofile.repository.CommuteProfileRepository;
import com.techeer.backend.domain.playlist.dto.PlaylistDetailResponse;
import com.techeer.backend.domain.playlist.entity.PlaylistType;
import com.techeer.backend.domain.playlist.entity.Track;
import com.techeer.backend.domain.playlist.entity.TrackMood;
import com.techeer.backend.domain.playlist.entity.TrackMoodScore;
import com.techeer.backend.domain.playlist.entity.TrackMoodSource;
import com.techeer.backend.domain.playlist.repository.TrackMoodScoreRepository;
import com.techeer.backend.domain.playlist.service.PlaylistService;
import com.techeer.backend.domain.recommendation.dto.PlaylistGenerateRequest;
import com.techeer.backend.domain.recommendation.dto.PlaylistGenerateResponse;
import com.techeer.backend.domain.weather.WeatherCondition;
import com.techeer.backend.domain.weather.WeatherSnapshot;
import com.techeer.backend.domain.weather.service.WeatherService;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class RecommendationServiceTest {

    private final CommuteProfileRepository profiles = mock(CommuteProfileRepository.class);
    private final TrackMoodScoreRepository scores = mock(TrackMoodScoreRepository.class);
    private final PlaylistService playlistService = mock(PlaylistService.class);
    private final WeatherService weatherService = mock(WeatherService.class);
    // 한국 시간 18시 = 퇴근 러시
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-16T09:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final RecommendationService service = new RecommendationService(
            profiles, scores, playlistService, weatherService, clock, RandomGenerator.of("L64X128MixRandom"));

    private CommuteProfile profile(long memberId, CommuteType type) {
        CommuteProfile p = CommuteProfile.builder()
                .memberId(memberId).commuteType(type).frequentRouteName("강남-판교")
                .departure(new Location(37.5, 127.0, "강남")).arrival(new Location(37.4, 127.1, "판교")).build();
        ReflectionTestUtils.setField(p, "id", 1L);
        return p;
    }

    private List<TrackMoodScore> rainyPool(int count) {
        List<TrackMoodScore> rows = new ArrayList<>();
        for (long i = 1; i <= count; i++) {
            Track t = Track.builder().itunesTrackId(i).title("곡" + i).artist("가수" + i).build();
            ReflectionTestUtils.setField(t, "id", i);
            rows.add(TrackMoodScore.create(t, TrackMood.GLOOMY, 0.9, TrackMoodSource.SCORED));
        }
        return rows;
    }

    private PlaylistDetailResponse emptyDetail() {
        return new PlaylistDetailResponse(10L, "x", PlaylistType.COMMUTE_GENERATED, null, true, 0, List.of());
    }

    @Test
    void 날씨_시간대로_곡을_골라_COMMUTE_GENERATED_플레이리스트를_만든다() {
        when(profiles.findById(1L)).thenReturn(Optional.of(profile(7L, CommuteType.FROM_WORK)));
        when(weatherService.getCurrentSnapshot(anyDouble(), anyDouble()))
                .thenReturn(new WeatherSnapshot(WeatherCondition.RAINY, 12.4, false, false));
        when(scores.findCandidates(any(), anyDouble())).thenReturn(rainyPool(30));
        when(playlistService.createGenerated(eq(7L), any(), anyList())).thenReturn(emptyDetail());

        PlaylistGenerateResponse response = service.generate(7L, new PlaylistGenerateRequest(1L, null, 10));

        ArgumentCaptor<List<Long>> trackIds = ArgumentCaptor.captor();
        ArgumentCaptor<String> name = ArgumentCaptor.captor();
        verify(playlistService).createGenerated(eq(7L), name.capture(), trackIds.capture());
        assertThat(trackIds.getValue()).hasSize(10).doesNotHaveDuplicates();
        assertThat(name.getValue()).isEqualTo("퇴근길 · 비 맞춤 플레이리스트");
        assertThat(response.context().timeBand()).isEqualTo(TimeBand.EVENING_RUSH);
        assertThat(response.context().weather().condition()).isEqualTo(WeatherCondition.RAINY);
        assertThat(response.context().weather().temperatureCelsius()).isEqualTo(12);
        assertThat(response.context().moods()).hasSize(3);
    }

    @Test
    void 이름과_곡_수를_지정하면_그대로_쓴다() {
        when(profiles.findById(1L)).thenReturn(Optional.of(profile(7L, CommuteType.TO_WORK)));
        when(weatherService.getCurrentSnapshot(anyDouble(), anyDouble()))
                .thenReturn(new WeatherSnapshot(WeatherCondition.RAINY, 12.0, false, false));
        when(scores.findCandidates(any(), anyDouble())).thenReturn(rainyPool(30));
        when(playlistService.createGenerated(any(), any(), anyList())).thenReturn(emptyDetail());

        service.generate(7L, new PlaylistGenerateRequest(1L, "내 이름", 5));

        ArgumentCaptor<List<Long>> trackIds = ArgumentCaptor.captor();
        verify(playlistService).createGenerated(eq(7L), eq("내 이름"), trackIds.capture());
        assertThat(trackIds.getValue()).hasSize(5);
    }

    @Test
    void 날씨_호출이_실패해도_시간대와_통근_구분만으로_추천한다() {
        when(profiles.findById(1L)).thenReturn(Optional.of(profile(7L, CommuteType.FROM_WORK)));
        when(weatherService.getCurrentSnapshot(anyDouble(), anyDouble()))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_ERROR));
        when(scores.findCandidates(any(), anyDouble())).thenReturn(rainyPool(30));
        when(playlistService.createGenerated(any(), any(), anyList())).thenReturn(emptyDetail());

        PlaylistGenerateResponse response = service.generate(7L, new PlaylistGenerateRequest(1L, null, null));

        assertThat(response.context().weather()).isNull();
        ArgumentCaptor<String> name = ArgumentCaptor.captor();
        verify(playlistService).createGenerated(eq(7L), name.capture(), anyList());
        assertThat(name.getValue()).isEqualTo("퇴근길 맞춤 플레이리스트");
    }

    @Test
    void 남의_통근_프로필이면_존재하지_않는_것으로_취급한다() {
        when(profiles.findById(1L)).thenReturn(Optional.of(profile(999L, CommuteType.TO_WORK)));

        BusinessException e = catchThrowableOfType(BusinessException.class,
                () -> service.generate(7L, new PlaylistGenerateRequest(1L, null, null)));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMUTE_PROFILE_NOT_FOUND);
        verify(playlistService, never()).createGenerated(any(), any(), anyList());
    }

    @Test
    void 추천할_곡이_하나도_없으면_PLAYLIST_GENERATE_NO_CANDIDATES() {
        when(profiles.findById(1L)).thenReturn(Optional.of(profile(7L, CommuteType.TO_WORK)));
        when(weatherService.getCurrentSnapshot(anyDouble(), anyDouble()))
                .thenReturn(new WeatherSnapshot(WeatherCondition.CLEAR, 20.0, false, false));
        when(scores.findCandidates(any(), anyDouble())).thenReturn(List.of());

        BusinessException e = catchThrowableOfType(BusinessException.class,
                () -> service.generate(7L, new PlaylistGenerateRequest(1L, null, null)));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PLAYLIST_GENERATE_NO_CANDIDATES);
        verify(playlistService, never()).createGenerated(any(), any(), anyList());
    }
}
