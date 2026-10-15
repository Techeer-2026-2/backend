package com.techeer.backend.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import com.techeer.backend.domain.ranking.entity.RankingModels.Chart;
import com.techeer.backend.domain.ranking.entity.RankingModels.Completion;
import com.techeer.backend.domain.ranking.service.RankingCalculator;
import com.techeer.backend.domain.ranking.service.RankingGeo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RankingCalculatorTest {
    private final RankingGeo geo = new RankingGeo();
    private final RankingCalculator calculator = new RankingCalculator(geo);
    private final String cell = geo.cell(37.5442, 127.0561);
    private final Instant at = Instant.parse("2026-10-03T02:30:00Z");

    RankingCalculatorTest() throws Exception {
    }

    @Test
    void deduplicatesListenersAcrossAdjacentCellsButKeepsTotalCompletions() {
        List<Completion> events = audience(20, "A", cell);
        String neighbor = geo.neighbors(cell).stream().filter(value -> !value.equals(cell)).findFirst().orElseThrow();
        events.add(play(1, "A", neighbor, at.plusSeconds(10)));
        events.add(events.getFirst()); // 동일 eventId 전달도 방어한다.
        Chart chart = chart(calculator.calculate(events), "NEARBY:" + cell);
        assertThat(chart.listeners()).isEqualTo(20);
        assertThat(chart.scores().getFirst().listeners()).isEqualTo(20);
        assertThat(chart.scores().getFirst().completions()).isEqualTo(21);
    }

    @Test
    void enforcesRegionAndTrackThresholdsIncludingGlobal() {
        assertThat(chart(calculator.calculate(audience(19, "A", cell)), "GLOBAL").scores()).isEmpty();
        List<Completion> events = audience(20, "A", cell);
        events.add(play(1, "hidden", cell, at));
        events.add(play(2, "hidden", cell, at));
        assertThat(chart(calculator.calculate(events), "GLOBAL").scores()).extracting(score -> score.trackId())
                .containsExactly("A");
        events.add(play(3, "hidden", cell, at));
        assertThat(chart(calculator.calculate(events), "GLOBAL").scores()).hasSize(2);
    }

    @Test
    void sortsByUniqueListenersThenCompletionsThenLatestThenTrackId() {
        List<Completion> events = audience(20, "winner", cell);
        for (String track : List.of("a", "b", "recent", "repeat")) {
            for (int user = 1; user <= 3; user++) {
                events.add(play(user, track, cell, track.equals("recent") ? at.plusSeconds(10) : at));
            }
        }
        events.add(play(1, "repeat", cell, at));
        assertThat(chart(calculator.calculate(events), "GLOBAL").scores()).extracting(score -> score.trackId())
                .containsExactly("winner", "repeat", "recent", "a", "b");
    }

    @Test
    void storesTopFiftyOnlyAfterFiltering() {
        List<Completion> events = new ArrayList<>();
        for (int i = 0; i < 55; i++) {
            events.addAll(audience(20, "track-%02d".formatted(i), cell));
        }
        assertThat(chart(calculator.calculate(events), "GLOBAL").scores()).hasSize(50);
        assertThat(chart(calculator.calculate(events), "GLOBAL").scores().getLast().trackId()).isEqualTo("track-49");
    }

    @Test
    void locationlessPlaysOnlyContributeToGlobal() {
        List<Chart> charts = calculator.calculate(audience(20, "A", null));
        assertThat(charts).hasSize(1);
        assertThat(charts.getFirst().key()).isEqualTo("GLOBAL");
        assertThat(charts.getFirst().scores().getFirst().listeners()).isEqualTo(20);
    }

    @Test
    void createsChartForEmptyCenterWhoseNeighborsHaveListeners() {
        String neighbor = geo.neighbors(cell).stream().filter(value -> !value.equals(cell)).findFirst().orElseThrow();
        assertThat(chart(calculator.calculate(audience(20, "A", neighbor)), "NEARBY:" + cell).scores()).hasSize(1);
    }

    private List<Completion> audience(int count, String track, String location) {
        List<Completion> events = new ArrayList<>();
        for (int user = 1; user <= count; user++) {
            events.add(play(user, track, location, at));
        }
        return events;
    }

    private Completion play(long user, String track, String location, Instant time) {
        return new Completion(UUID.randomUUID(), user, track, time, location, null, null);
    }

    private Chart chart(List<Chart> charts, String key) {
        return charts.stream().filter(chart -> chart.key().equals(key)).findFirst().orElseThrow();
    }
}
