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
 * 만들어진 플레이리스트(id·name·type·tracks, 플레이리스트 상세 조회와 같은 모양)와, 어떤 상황을 보고 만들었는지(context).
 * 프론트가 {@code result.id}, {@code result.name} 으로 바로 읽도록 한 겹으로 펼쳐 둔다.
 * 프론트는 context 로 "맑음 22°C · 출근길" 같은 문구를 보여줄 수 있다.
 */
public record PlaylistGenerateResponse(
        Long id, String name, PlaylistType type, List<PlaylistTrackResponse> tracks, Context context) {

    /** @param weather 날씨 조회에 실패했으면 null */
    public record Context(Weather weather, TimeBand timeBand, CommuteType commuteType, List<String> moods) {
    }

    public record Weather(WeatherCondition condition, Integer temperatureCelsius, boolean hazy, boolean stormy) {
    }

    public static PlaylistGenerateResponse of(PlaylistDetailResponse playlist, RecommendationContext context,
            List<String> topMoods) {
        WeatherSnapshot w = context.weather();
        Weather weather = w == null ? null : new Weather(w.condition(),
                w.temperatureCelsius() == null ? null : (int) Math.round(w.temperatureCelsius()), w.hazy(), w.stormy());
        return new PlaylistGenerateResponse(playlist.id(), playlist.name(), playlist.type(), playlist.tracks(),
                new Context(weather, context.timeBand(), context.commuteType(), topMoods));
    }
}
