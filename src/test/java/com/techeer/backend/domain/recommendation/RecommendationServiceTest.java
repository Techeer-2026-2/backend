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
import com.techeer.backend.domain.commuteprofile.entity.CommuteRoute;
import com.techeer.backend.domain.commuteprofile.entity.CommuteType;
import com.techeer.backend.domain.commuteprofile.entity.Location;
import com.techeer.backend.domain.commuteprofile.repository.CommuteProfileRepository;
import com.techeer.backend.domain.commuteprofile.repository.CommuteRouteRepository;
import com.techeer.backend.domain.playlist.dto.PlaylistDetailResponse;
import com.techeer.backend.domain.playlist.entity.PlaylistType;
import com.techeer.backend.domain.playlist.entity.Track;
import com.techeer.backend.domain.playlist.entity.TrackMood;
import com.techeer.backend.domain.playlist.entity.TrackMoodScore;
import com.techeer.backend.domain.playlist.entity.TrackMoodSource;
import com.techeer.backend.domain.playlist.repository.TrackMoodScoreRepository;
import com.techeer.backend.domain.playlist.service.PlaylistService;
import com.techeer.backend.domain.playlist.service.RunningTimeAdjuster;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class RecommendationServiceTest {

    private final CommuteProfileRepository profiles = mock(CommuteProfileRepository.class);
    private final CommuteRouteRepository routes = mock(CommuteRouteRepository.class);
    private final TrackMoodScoreRepository scores = mock(TrackMoodScoreRepository.class);
    private final PlaylistService playlistService = mock(PlaylistService.class);
    private final WeatherService weatherService = mock(WeatherService.class);
    private final NearbyPopularityProvider popularityProvider = mock(NearbyPopularityProvider.class);
    // 한국 시간 18시 = 퇴근 러시
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-16T09:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final RecommendationService service = new RecommendationService(
            profiles, routes, scores, playlistService, new RunningTimeAdjuster(), weatherService, popularityProvider,
            clock, RandomGenerator.of("L64X128MixRandom"));

    @BeforeEach
    void noPopularityByDefault() {
        when(popularityProvider.find(any(), any())).thenReturn(NearbyPopularity.none());
    }

    private CommuteProfile profile(long memberId, CommuteType type) {
        CommuteProfile p = CommuteProfile.builder()
                .memberId(memberId).commuteType(type).frequentRouteName("강남-판교")
                .departure(new Location(37.5, 127.0, "강남")).arrival(new Location(37.4, 127.1, "판교")).build();
        ReflectionTestUtils.setField(p, "id", 1L);
        return p;
    }

    private List<TrackMoodScore> rainyPool(int count) {
        return rainyPool(count, null);
    }

    private List<TrackMoodScore> rainyPool(int count, Integer trackTimeMillis) {
        List<TrackMoodScore> rows = new ArrayList<>();
        for (long i = 1; i <= count; i++) {
            Track t = Track.builder().itunesTrackId(i).title("곡" + i).artist("가수" + i)
                    .trackTimeMillis(trackTimeMillis).build();
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
    void 곡_수를_정하지_않으면_통근_소요시간에_맞춰_곡_수를_정한다() {
        stubRainyProfileWithRoute(30);
        when(scores.findCandidates(any(), anyDouble())).thenReturn(rainyPool(40, 180_000));

        service.generate(7L, new PlaylistGenerateRequest(1L, null, null));

        // 3분 곡 10개 = 30분
        ArgumentCaptor<List<Long>> trackIds = ArgumentCaptor.captor();
        verify(playlistService).createGenerated(eq(7L), any(), trackIds.capture());
        assertThat(trackIds.getValue()).hasSize(10).doesNotHaveDuplicates();
    }

    @Test
    void 곡_수를_정하면_통근_소요시간보다_곡_수가_우선이다() {
        stubRainyProfileWithRoute(30);
        when(scores.findCandidates(any(), anyDouble())).thenReturn(rainyPool(40, 180_000));

        service.generate(7L, new PlaylistGenerateRequest(1L, null, 4));

        ArgumentCaptor<List<Long>> trackIds = ArgumentCaptor.captor();
        verify(playlistService).createGenerated(eq(7L), any(), trackIds.capture());
        assertThat(trackIds.getValue()).hasSize(4);
    }

    @Test
    void 재생시간을_아는_곡이_없으면_기본_곡_수로_만든다() {
        stubRainyProfileWithRoute(30);
        when(scores.findCandidates(any(), anyDouble())).thenReturn(rainyPool(40));

        service.generate(7L, new PlaylistGenerateRequest(1L, null, null));

        ArgumentCaptor<List<Long>> trackIds = ArgumentCaptor.captor();
        verify(playlistService).createGenerated(eq(7L), any(), trackIds.capture());
        assertThat(trackIds.getValue()).hasSize(RecommendationService.DEFAULT_SIZE);
    }

    private void stubRainyProfileWithRoute(int averageDurationMinutes) {
        CommuteProfile profile = profile(7L, CommuteType.TO_WORK);
        when(profiles.findById(1L)).thenReturn(Optional.of(profile));
        when(routes.findByCommuteProfileId(1L)).thenReturn(Optional.of(CommuteRoute.builder()
                .commuteProfile(profile).averageDurationMinutes(averageDurationMinutes).build()));
        when(weatherService.getCurrentSnapshot(anyDouble(), anyDouble()))
                .thenReturn(new WeatherSnapshot(WeatherCondition.RAINY, 12.0, false, false));
        when(playlistService.createGenerated(any(), any(), anyList())).thenReturn(emptyDetail());
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

    @Test
    void 요청에_위치가_있으면_그_위치로_주변_인기곡을_조회한다() {
        when(profiles.findById(1L)).thenReturn(Optional.of(profile(7L, CommuteType.TO_WORK)));
        when(weatherService.getCurrentSnapshot(anyDouble(), anyDouble()))
                .thenReturn(new WeatherSnapshot(WeatherCondition.RAINY, 12.0, false, false));
        when(scores.findCandidates(any(), anyDouble())).thenReturn(rainyPool(30));
        when(playlistService.createGenerated(any(), any(), anyList())).thenReturn(emptyDetail());

        service.generate(7L, new PlaylistGenerateRequest(1L, null, 5, 37.55, 127.04));

        verify(popularityProvider).find(37.55, 127.04);
        verify(popularityProvider, never()).find(eq(37.5), eq(127.0));
    }

    @Test
    void 요청에_위치가_없으면_통근_프로필_출발지로_조회한다() {
        when(profiles.findById(1L)).thenReturn(Optional.of(profile(7L, CommuteType.TO_WORK)));
        when(weatherService.getCurrentSnapshot(anyDouble(), anyDouble()))
                .thenReturn(new WeatherSnapshot(WeatherCondition.RAINY, 12.0, false, false));
        when(scores.findCandidates(any(), anyDouble())).thenReturn(rainyPool(30));
        when(playlistService.createGenerated(any(), any(), anyList())).thenReturn(emptyDetail());

        service.generate(7L, new PlaylistGenerateRequest(1L, null, 5));

        verify(popularityProvider).find(37.5, 127.0);
    }

    @Test
    void 위도와_경도_중_하나만_보내면_INVALID_INPUT() {
        when(profiles.findById(1L)).thenReturn(Optional.of(profile(7L, CommuteType.TO_WORK)));

        BusinessException e = catchThrowableOfType(BusinessException.class,
                () -> service.generate(7L, new PlaylistGenerateRequest(1L, null, 5, 37.55, null)));

        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        verify(playlistService, never()).createGenerated(any(), any(), anyList());
    }

    @Test
    void 응답에_주변_인기곡을_반영했는지와_차트_범위를_담는다() {
        when(profiles.findById(1L)).thenReturn(Optional.of(profile(7L, CommuteType.TO_WORK)));
        when(weatherService.getCurrentSnapshot(anyDouble(), anyDouble()))
                .thenReturn(new WeatherSnapshot(WeatherCondition.RAINY, 12.0, false, false));
        when(scores.findCandidates(any(), anyDouble())).thenReturn(rainyPool(12));
        when(playlistService.createGenerated(any(), any(), anyList())).thenReturn(emptyDetail());
        // 1~3번 곡이 주변 차트에 있다. 후보가 12곡뿐이고 12곡을 모두 뽑으므로 세 곡 모두 포함된다.
        when(popularityProvider.find(any(), any())).thenReturn(
                new NearbyPopularity(java.util.Map.of(1L, 0.5, 2L, 0.3, 3L, 0.1), "PARENT", "성수동 일대"));

        PlaylistGenerateResponse response = service.generate(7L, new PlaylistGenerateRequest(1L, null, 12));

        assertThat(response.context().popularity().applied()).isTrue();
        assertThat(response.context().popularity().scope()).isEqualTo("PARENT");
        assertThat(response.context().popularity().locationLabel()).isEqualTo("성수동 일대");
        assertThat(response.context().popularity().boostedTracks()).isEqualTo(3);
    }

    @Test
    void 주변_차트의_곡이_이번_후보에_없으면_반영하지_않았다고_알린다() {
        when(profiles.findById(1L)).thenReturn(Optional.of(profile(7L, CommuteType.TO_WORK)));
        when(weatherService.getCurrentSnapshot(anyDouble(), anyDouble()))
                .thenReturn(new WeatherSnapshot(WeatherCondition.RAINY, 12.0, false, false));
        when(scores.findCandidates(any(), anyDouble())).thenReturn(rainyPool(12));
        when(playlistService.createGenerated(any(), any(), anyList())).thenReturn(emptyDetail());
        // 차트에는 곡이 있지만 후보(1~12번)에 없는 곡이다
        when(popularityProvider.find(any(), any())).thenReturn(
                new NearbyPopularity(java.util.Map.of(9999L, 0.5), "NEARBY", "성수동 주변"));

        PlaylistGenerateResponse response = service.generate(7L, new PlaylistGenerateRequest(1L, null, 12));

        assertThat(response.context().popularity().applied()).isFalse();
        assertThat(response.context().popularity().scope()).isEqualTo("NONE");
        assertThat(response.context().popularity().locationLabel()).isNull();
        assertThat(response.context().popularity().boostedTracks()).isZero();
    }

    @Test
    void 가산점을_받는_후보가_있으면_이번에_뽑히지_않았어도_반영했다고_알린다() {
        when(profiles.findById(1L)).thenReturn(Optional.of(profile(7L, CommuteType.TO_WORK)));
        when(weatherService.getCurrentSnapshot(anyDouble(), anyDouble()))
                .thenReturn(new WeatherSnapshot(WeatherCondition.RAINY, 12.0, false, false));
        when(scores.findCandidates(any(), anyDouble())).thenReturn(rainyPool(30));
        when(playlistService.createGenerated(any(), any(), anyList())).thenReturn(emptyDetail());
        when(popularityProvider.find(any(), any())).thenReturn(
                new NearbyPopularity(java.util.Map.of(1L, 0.5), "NEARBY", "성수동 주변"));

        // 30곡 후보에서 1곡만 뽑으면 1번 곡이 안 뽑힐 수 있다. 그래도 반영은 했다(applied)고 알려야 한다.
        PlaylistGenerateResponse response = service.generate(7L, new PlaylistGenerateRequest(1L, null, 1));

        assertThat(response.context().popularity().applied()).isTrue();
        assertThat(response.context().popularity().scope()).isEqualTo("NEARBY");
        assertThat(response.context().popularity().boostedTracks()).isBetween(0, 1);
    }

    @Test
    void 주변_차트를_쓰지_못하면_반영하지_않았다고_알린다() {
        when(profiles.findById(1L)).thenReturn(Optional.of(profile(7L, CommuteType.TO_WORK)));
        when(weatherService.getCurrentSnapshot(anyDouble(), anyDouble()))
                .thenReturn(new WeatherSnapshot(WeatherCondition.RAINY, 12.0, false, false));
        when(scores.findCandidates(any(), anyDouble())).thenReturn(rainyPool(30));
        when(playlistService.createGenerated(any(), any(), anyList())).thenReturn(emptyDetail());

        PlaylistGenerateResponse response = service.generate(7L, new PlaylistGenerateRequest(1L, null, 5));

        assertThat(response.context().popularity().applied()).isFalse();
        assertThat(response.context().popularity().scope()).isEqualTo("NONE");
        assertThat(response.context().popularity().boostedTracks()).isZero();
    }

    @Test
    void 주변_차트에_있는_곡이_실제로_더_자주_뽑힌다() {
        when(profiles.findById(1L)).thenReturn(Optional.of(profile(7L, CommuteType.TO_WORK)));
        when(weatherService.getCurrentSnapshot(anyDouble(), anyDouble()))
                .thenReturn(new WeatherSnapshot(WeatherCondition.RAINY, 12.0, false, false));
        when(scores.findCandidates(any(), anyDouble())).thenReturn(rainyPool(20));
        when(playlistService.createGenerated(any(), any(), anyList())).thenReturn(emptyDetail());

        int plain = timesTrackOneIsPicked(300);
        when(popularityProvider.find(any(), any())).thenReturn(
                new NearbyPopularity(java.util.Map.of(1L, 5.0), "NEARBY", "성수동 주변"));
        int boosted = timesTrackOneIsPicked(300);

        // 20곡 중 1곡만 뽑으면 가산점이 없을 때 1번 곡은 5% 근처, 가산점 5.0(36배)이면 65% 근처
        assertThat(plain).isLessThan(60);
        assertThat(boosted).isGreaterThan(120);
    }

    private int timesTrackOneIsPicked(int rounds) {
        int picked = 0;
        for (int i = 0; i < rounds; i++) {
            org.mockito.Mockito.clearInvocations(playlistService);
            service.generate(7L, new PlaylistGenerateRequest(1L, null, 1));
            ArgumentCaptor<List<Long>> trackIds = ArgumentCaptor.captor();
            verify(playlistService).createGenerated(eq(7L), any(), trackIds.capture());
            picked += trackIds.getValue().contains(1L) ? 1 : 0;
        }
        return picked;
    }
}
