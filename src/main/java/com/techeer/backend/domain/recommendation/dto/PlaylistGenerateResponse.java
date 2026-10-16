package com.techeer.backend.domain.recommendation.dto;

import com.techeer.backend.domain.commuteprofile.entity.CommuteType;
import com.techeer.backend.domain.playlist.dto.PlaylistDetailResponse;
import com.techeer.backend.domain.playlist.dto.PlaylistTrackResponse;
import com.techeer.backend.domain.playlist.entity.PlaylistType;
import com.techeer.backend.domain.recommendation.RecommendationContext;
import com.techeer.backend.domain.recommendation.TimeBand;
import com.techeer.backend.domain.weather.WeatherCondition;
import com.techeer.backend.domain.weather.WeatherSnapshot;
import java.util.List;

/**
 * 만들어진 플레이리스트(id·name·type·totalDurationSec·tracks, 플레이리스트 상세 조회와 같은 모양)와, 어떤 상황을 보고 만들었는지(context).
 * 프론트가 {@code result.id}, {@code result.name} 으로 바로 읽도록 한 겹으로 펼쳐 둔다.
 * 프론트는 context 로 "맑음 22°C · 출근길" 같은 문구를 보여줄 수 있다.
 */
public record PlaylistGenerateResponse(
        Long id, String name, PlaylistType type, int totalDurationSec, List<PlaylistTrackResponse> tracks,
        Context context) {

    /** @param weather 날씨 조회에 실패했으면 null */
    public record Context(Weather weather, TimeBand timeBand, CommuteType commuteType, List<String> moods,
            Popularity popularity) {
    }

    /**
     * 주변 인기곡을 추천에 반영했는지.
     *
     * @param applied 반영했으면 true. 위치를 모르거나 주변 차트가 없거나 아직 집계 전이면 false
     * @param scope 쓴 차트 범위(NEARBY/PARENT/CITY/GLOBAL), 반영하지 않았으면 NONE. GLOBAL 이면 주변 데이터가 없어 전체 차트로 대신한 것이다
     * @param locationLabel 차트 이름(예: "성수동 주변"), 반영하지 않았으면 null
     * @param boostedTracks 뽑힌 곡 중 주변 차트에도 있는 곡 수
     */
    public record Popularity(boolean applied, String scope, String locationLabel, int boostedTracks) {
    }

    public record Weather(WeatherCondition condition, Integer temperatureCelsius, boolean hazy, boolean stormy) {
    }

    public static PlaylistGenerateResponse of(PlaylistDetailResponse playlist, RecommendationContext context,
            List<String> topMoods, Popularity popularity) {
        WeatherSnapshot w = context.weather();
        Weather weather = w == null ? null : new Weather(w.condition(),
                w.temperatureCelsius() == null ? null : (int) Math.round(w.temperatureCelsius()), w.hazy(), w.stormy());
        return new PlaylistGenerateResponse(playlist.id(), playlist.name(), playlist.type(),
                playlist.totalDurationSec(), playlist.tracks(),
                new Context(weather, context.timeBand(), context.commuteType(), topMoods, popularity));
    }
}
