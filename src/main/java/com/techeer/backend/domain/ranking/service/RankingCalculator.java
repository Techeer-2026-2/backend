package com.techeer.backend.domain.ranking.service;

import com.techeer.backend.domain.ranking.entity.RankingModels.Chart;
import com.techeer.backend.domain.ranking.entity.RankingModels.Completion;
import com.techeer.backend.domain.ranking.entity.RankingModels.Score;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** P1: 한 시간의 유효 이벤트를 집계. 지역 간 합산도 사용자 집합의 합집합을 사용한다. */
@Component
@RequiredArgsConstructor
public class RankingCalculator {
    public static final int MIN_REGION_LISTENERS = 20;
    public static final int MIN_TRACK_LISTENERS = 3;
    public static final int MAX_ITEMS = 50;
    private final RankingGeo geo;

    public List<Chart> calculate(List<Completion> events) {
        Map<String, Accumulator> scopes = new HashMap<>();
        scopes.put("GLOBAL", new Accumulator("전체 인기곡"));
        Set<java.util.UUID> seen = new HashSet<>();
        Map<String, List<String>> neighborhoods = new HashMap<>();
        Map<String, String> parents = new HashMap<>();
        for (Completion event : events) {
            if (!seen.add(event.eventId())) {
                continue;
            }
            add(scopes, "GLOBAL", "전체 인기곡", event);
            if (event.h3Cell() == null) {
                continue;
            }
            // 각 이벤트 셀을 포함하는 모든 조회 중심 셀에 미리 집계한다.
            for (String center : neighborhoods.computeIfAbsent(event.h3Cell(), geo::neighbors)) {
                add(scopes, "NEARBY:" + center, "현재 위치 주변", event);
            }
            String parent = parents.computeIfAbsent(event.h3Cell(), geo::parent);
            add(scopes, "PARENT:" + parent, "주변 광역 인기곡", event);
            if (event.cityId() != null) {
                add(scopes, "CITY:" + event.cityId(), event.cityLabel() + " 인기곡", event);
            }
        }
        return scopes.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().finish(entry.getKey())).toList();
    }

    private void add(Map<String, Accumulator> scopes, String key, String label, Completion event) {
        scopes.computeIfAbsent(key, ignored -> new Accumulator(label)).add(event);
    }

    private static class Accumulator {
        private final String label;
        private final Set<Long> listeners = new HashSet<>();
        private final Map<String, List<Completion>> tracks = new HashMap<>();

        Accumulator(String label) {
            this.label = label;
        }

        void add(Completion event) {
            listeners.add(event.userId());
            tracks.computeIfAbsent(event.trackId(), ignored -> new ArrayList<>()).add(event);
        }

        Chart finish(String key) {
            if (listeners.size() < MIN_REGION_LISTENERS) {
                return new Chart(key, label, listeners.size(), List.of());
            }
            List<Score> scores = tracks.entrySet().stream().map(entry -> {
                List<Completion> plays = entry.getValue();
                return new Score(entry.getKey(), plays.stream().map(Completion::userId).distinct().count(),
                        plays.size(), plays.stream().map(Completion::occurredAt).max(Comparator.naturalOrder()).orElseThrow());
            }).filter(score -> score.listeners() >= MIN_TRACK_LISTENERS)
                    .sorted(Comparator.comparingLong(Score::listeners).reversed()
                            .thenComparing(Comparator.comparingLong(Score::completions).reversed())
                            .thenComparing(Score::lastCompletedAt, Comparator.reverseOrder())
                            .thenComparing(Score::trackId))
                    .limit(MAX_ITEMS).toList();
            return new Chart(key, label, listeners.size(), scores);
        }
    }
}
