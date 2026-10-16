package com.techeer.backend.domain.recommendation;

import com.techeer.backend.domain.commuteprofile.entity.CommuteProfile;
import com.techeer.backend.domain.commuteprofile.entity.Location;
import com.techeer.backend.domain.commuteprofile.repository.CommuteProfileRepository;
import com.techeer.backend.domain.playlist.dto.PlaylistDetailResponse;
import com.techeer.backend.domain.playlist.entity.Track;
import com.techeer.backend.domain.playlist.entity.TrackMood;
import com.techeer.backend.domain.playlist.entity.TrackMoodScore;
import com.techeer.backend.domain.playlist.repository.TrackMoodScoreRepository;
import com.techeer.backend.domain.playlist.service.PlaylistService;
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

    private final CommuteProfileRepository commuteProfileRepository;
    private final TrackMoodScoreRepository moodScoreRepository;
    private final PlaylistService playlistService;
    private final WeatherService weatherService;
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
        int size = request.size() == null ? DEFAULT_SIZE : request.size();
        List<Track> selected = PlaylistRecommender.select(candidates, weights, size, random);
        if (selected.isEmpty()) {
            throw new BusinessException(ErrorCode.PLAYLIST_GENERATE_NO_CANDIDATES);
        }

        String name = request.name() != null && !request.name().isBlank() ? request.name() : defaultName(context);
        PlaylistDetailResponse playlist = playlistService.createGenerated(
                memberId, name, selected.stream().map(Track::getId).toList());
        return PlaylistGenerateResponse.of(playlist, context, topMoods(weights));
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
