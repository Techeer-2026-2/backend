package com.techeer.backend.domain.recommendation;

import com.techeer.backend.domain.commuteprofile.entity.CommuteProfile;
import com.techeer.backend.domain.commuteprofile.entity.CommuteRoute;
import com.techeer.backend.domain.commuteprofile.entity.Location;
import com.techeer.backend.domain.commuteprofile.repository.CommuteProfileRepository;
import com.techeer.backend.domain.commuteprofile.repository.CommuteRouteRepository;
import com.techeer.backend.domain.playlist.dto.PlaylistDetailResponse;
import com.techeer.backend.domain.playlist.entity.Track;
import com.techeer.backend.domain.playlist.entity.TrackMood;
import com.techeer.backend.domain.playlist.entity.TrackMoodScore;
import com.techeer.backend.domain.playlist.repository.TrackMoodScoreRepository;
import com.techeer.backend.domain.playlist.service.PlaylistService;
import com.techeer.backend.domain.playlist.service.RunningTimeAdjuster;
import com.techeer.backend.domain.recommendation.PlaylistRecommender.Candidate;
import com.techeer.backend.domain.recommendation.dto.PlaylistGenerateRequest;
import com.techeer.backend.domain.recommendation.dto.PlaylistGenerateResponse;
import com.techeer.backend.domain.weather.WeatherSnapshot;
import com.techeer.backend.domain.weather.service.WeatherService;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.random.RandomGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 날씨·시간대·통근 구분으로 플레이리스트를 자동 생성한다.
 *
 * <p>이 클래스에는 {@code @Transactional} 을 붙이지 않는다. 날씨 호출(외부 네트워크)을 하는 동안 DB 트랜잭션을
 * 열어 두지 않으려는 것이고, DB 에 쓰는 마지막 단계만 {@link PlaylistService#createGenerated} 의 트랜잭션에서 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    static final int DEFAULT_SIZE = 15;
    /** 이 점수 미만의 분위기 태그는 후보에서 뺀다 */
    static final double MIN_MOOD_SCORE = 0.2;
    private static final int TOP_MOODS_IN_RESPONSE = 3;
    /** 러닝타임 맞춤에서 어림한 곡 수보다 더 뽑아 두는 곡 수. 긴 곡을 건너뛰고 짧은 곡으로 채울 여유 */
    static final int RUNNING_TIME_POOL_SLACK = 5;

    private final CommuteProfileRepository commuteProfileRepository;
    private final CommuteRouteRepository commuteRouteRepository;
    private final TrackMoodScoreRepository moodScoreRepository;
    private final PlaylistService playlistService;
    private final RunningTimeAdjuster runningTimeAdjuster;
    private final WeatherService weatherService;
    private final NearbyPopularityProvider popularityProvider;
    private final Clock clock;
    private final RandomGenerator random;

    public PlaylistGenerateResponse generate(Long memberId, PlaylistGenerateRequest request) {
        CommuteProfile profile = commuteProfileRepository.findById(request.commuteProfileId())
                .filter(p -> p.getMemberId().equals(memberId))   // 남의 프로필은 존재 자체를 숨긴다
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMUTE_PROFILE_NOT_FOUND));

        RecommendationContext context = new RecommendationContext(
                fetchWeatherOrNull(profile.getDeparture()),
                TimeBand.of(LocalTime.now(clock)),
                profile.getCommuteType());
        Map<TrackMood, Double> weights = MoodRules.weights(context);

        List<Candidate> candidates = loadCandidates(weights);
        NearbyPopularity popularity = effectivePopularity(
                findPopularity(request, profile.getDeparture()), candidates, weights);
        Integer targetMinutes = request.size() == null ? commuteMinutesOrNull(profile) : null;
        List<Track> selected = targetMinutes == null
                ? PlaylistRecommender.select(candidates, weights, sizeOrDefault(request), random, popularity)
                : selectForRunningTime(candidates, weights, targetMinutes, popularity);
        if (selected.isEmpty()) {
            throw new BusinessException(ErrorCode.PLAYLIST_GENERATE_NO_CANDIDATES);
        }

        String name = request.name() != null && !request.name().isBlank() ? request.name() : defaultName(context);
        PlaylistDetailResponse playlist = playlistService.createGenerated(
                memberId, name, selected.stream().map(Track::getId).toList());
        long boosted = selected.stream().filter(t -> popularity.boostOf(t.getItunesTrackId()) > 0).count();
        return PlaylistGenerateResponse.of(playlist, context, topMoods(weights),
                new PlaylistGenerateResponse.Popularity(
                        popularity.applied(), popularity.scope(), popularity.locationLabel(), (int) boosted));
    }

    /**
     * 가산점이 이번 추천에 실제로 쓰일 수 있는지 가려, 쓰일 수 없으면 "반영 안 함"으로 바꾼다.
     * 차트에 연결 가능한 곡이 있어도 그 곡들이 이번 후보에 없거나 후보 자격(최소 적합도)에 못 미치면 아무 영향이 없으므로,
     * 응답이 반영했다고 알리지 않도록 후보 안에서 가산점을 받는 곡이 있는지 확인한다.
     */
    private static NearbyPopularity effectivePopularity(
            NearbyPopularity popularity, List<Candidate> candidates, Map<TrackMood, Double> weights) {
        boolean usable = popularity.applied() && candidates.stream().anyMatch(c ->
                PlaylistRecommender.relevance(c, weights) >= PlaylistRecommender.MIN_RELEVANCE
                        && popularity.boostOf(c.track().getItunesTrackId()) > 0);
        return usable ? popularity : NearbyPopularity.none();
    }

    /**
     * 주변 인기곡 가산점의 기준 위치를 정한다. 요청에 위치가 있으면 그것을, 없으면 통근 프로필 출발지를 쓴다.
     * 위도·경도 중 하나만 보내면 어느 쪽 위치인지 알 수 없으므로 거절한다.
     */
    private NearbyPopularity findPopularity(PlaylistGenerateRequest request, Location departure) {
        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (request.latitude() != null) {
            return popularityProvider.find(request.latitude(), request.longitude());
        }
        if (departure == null || departure.getLatitude() == null || departure.getLongitude() == null) {
            return NearbyPopularity.none();
        }
        return popularityProvider.find(departure.getLatitude(), departure.getLongitude());
    }

    private static int sizeOrDefault(PlaylistGenerateRequest request) {
        return request.size() == null ? DEFAULT_SIZE : request.size();
    }

    /** 곡 수를 직접 정하지 않았을 때만 쓴다. 경로가 없거나 소요시간을 모르면 null (기본 곡 수로 만든다). */
    private Integer commuteMinutesOrNull(CommuteProfile profile) {
        return commuteRouteRepository.findByCommuteProfileId(profile.getId())
                .map(CommuteRoute::getAverageDurationMinutes)
                .filter(minutes -> minutes > 0)
                .orElse(null);
    }

    /**
     * 통근 소요시간에 맞춰 곡을 고른다 (#90). 필요한 곡 수를 평균 재생시간으로 어림해 그보다 조금 넉넉하게 뽑고,
     * {@link RunningTimeAdjuster} 가 그 순서대로 시간을 맞춘다. 넉넉하게 뽑는 양을 작게 두는 것은
     * 가수·장르 제한이 실제로 담기는 곡 수 기준으로 걸리게 하려는 것이다.
     * 재생시간을 아는 후보가 없어 시간을 못 맞추면 기본 곡 수로 만든다.
     */
    private List<Track> selectForRunningTime(List<Candidate> candidates, Map<TrackMood, Double> weights,
            int targetMinutes, NearbyPopularity popularity) {
        OptionalDouble averageMillis = candidates.stream()
                .map(c -> c.track().getTrackTimeMillis())
                .filter(millis -> millis != null && millis > 0)
                .mapToInt(Integer::intValue)
                .average();
        if (averageMillis.isEmpty()) {
            return PlaylistRecommender.select(candidates, weights, DEFAULT_SIZE, random, popularity);
        }
        int estimated = (int) Math.ceil(targetMinutes * 60_000.0 / averageMillis.getAsDouble());
        int poolSize = Math.min(estimated + RUNNING_TIME_POOL_SLACK, RunningTimeAdjuster.MAX_TRACKS);
        List<Track> fitted = runningTimeAdjuster.fit(
                PlaylistRecommender.select(candidates, weights, poolSize, random, popularity), targetMinutes);
        return fitted.isEmpty()
                ? PlaylistRecommender.select(candidates, weights, DEFAULT_SIZE, random, popularity) : fitted;
    }

    /** 날씨를 못 가져와도 추천은 한다 (시간대와 통근 구분만으로). */
    private WeatherSnapshot fetchWeatherOrNull(Location departure) {
        if (departure == null || departure.getLatitude() == null || departure.getLongitude() == null) {
            return null;
        }
        try {
            return weatherService.getCurrentSnapshot(departure.getLatitude(), departure.getLongitude());
        } catch (BusinessException e) {
            log.warn("날씨 조회에 실패해 시간대·통근 구분만으로 추천합니다.");
            return null;
        }
    }

    private List<Candidate> loadCandidates(Map<TrackMood, Double> weights) {
        Map<Long, Map<TrackMood, Double>> scoresByTrack = new LinkedHashMap<>();
        Map<Long, Track> tracks = new LinkedHashMap<>();
        for (TrackMoodScore s : moodScoreRepository.findCandidates(weights.keySet(), MIN_MOOD_SCORE)) {
            tracks.putIfAbsent(s.getTrack().getId(), s.getTrack());
            scoresByTrack.computeIfAbsent(s.getTrack().getId(), id -> new EnumMap<>(TrackMood.class))
                    .put(s.getMood(), s.getScore());
        }
        List<Candidate> result = new ArrayList<>();
        scoresByTrack.forEach((id, scores) -> result.add(new Candidate(tracks.get(id), scores)));
        return result;
    }

    private static List<String> topMoods(Map<TrackMood, Double> weights) {
        return weights.entrySet().stream()
                .sorted(Map.Entry.<TrackMood, Double>comparingByValue().reversed())
                .limit(TOP_MOODS_IN_RESPONSE)
                .map(e -> e.getKey().name())
                .toList();
    }

    static String defaultName(RecommendationContext context) {
        String label = switch (context.commuteType()) {
            case TO_WORK -> "출근길";
            case FROM_WORK -> "퇴근길";
            case OTHER -> "이동길";
        };
        WeatherSnapshot w = context.weather();
        if (w == null) {
            return label + " 맞춤 플레이리스트";
        }
        String weather = switch (w.condition()) {
            case CLEAR -> "맑음";
            case CLOUDY -> "흐림";
            case RAINY -> "비";
            case SNOWY -> "눈";
        };
        return label + " · " + weather + " 맞춤 플레이리스트";
    }
}
