package com.techeer.backend.domain.playlist.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.techeer.backend.domain.playlist.entity.Track;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RunningTimeAdjusterTest {

    private final RunningTimeAdjuster adjuster = new RunningTimeAdjuster();

    @Test
    void 목표_시간을_넘지_않는_만큼_앞에서부터_담는다() {
        List<Track> candidates = List.of(
                track(1L, minutes(4)), track(2L, minutes(3)), track(3L, minutes(3)), track(4L, minutes(4)));

        List<Track> result = adjuster.fit(candidates, 10);

        assertThat(ids(result)).containsExactly(1L, 2L, 3L);
    }

    @Test
    void 넘치는_곡은_건너뛰고_뒤의_짧은_곡으로_채운다() {
        List<Track> candidates = List.of(
                track(1L, minutes(6)), track(2L, minutes(5)), track(3L, minutes(4)));

        List<Track> result = adjuster.fit(candidates, 10);

        assertThat(ids(result)).containsExactly(1L, 3L);
    }

    @Test
    void 남은_공백보다_덜_넘치는_곡이_있으면_하나_더_붙인다() {
        // 8분 담고 2분이 비는데, 3분 곡을 붙이면 1분만 넘친다
        List<Track> candidates = List.of(
                track(1L, minutes(4)), track(2L, minutes(4)), track(3L, minutes(5)), track(4L, minutes(3)));

        List<Track> result = adjuster.fit(candidates, 10);

        assertThat(ids(result)).containsExactly(1L, 2L, 4L);
    }

    @Test
    void 붙이면_공백보다_더_넘치는_곡은_붙이지_않는다() {
        // 8분 담고 2분이 비는데, 5분 곡을 붙이면 3분이나 넘친다
        List<Track> candidates = List.of(
                track(1L, minutes(4)), track(2L, minutes(4)), track(3L, minutes(5)));

        List<Track> result = adjuster.fit(candidates, 10);

        assertThat(ids(result)).containsExactly(1L, 2L);
    }

    @Test
    void 재생시간이나_곡_ID를_모르는_곡과_중복_곡은_제외한다() {
        List<Track> candidates = List.of(
                track(1L, null), track(2L, 0), track(null, minutes(3)),
                track(3L, minutes(3)), track(3L, minutes(3)));

        List<Track> result = adjuster.fit(candidates, 10);

        assertThat(ids(result)).containsExactly(3L);
    }

    @Test
    void 목표_시간이_0분_이하면_빈_목록을_돌려준다() {
        List<Track> candidates = List.of(track(1L, minutes(3)));

        assertThat(adjuster.fit(candidates, 0)).isEmpty();
    }

    @Test
    void 최대_곡_수를_넘기지_않는다() {
        List<Track> candidates = new ArrayList<>();
        IntStream.rangeClosed(1, RunningTimeAdjuster.MAX_TRACKS + 1)
                .forEach(id -> candidates.add(track((long) id, 1000)));

        List<Track> result = adjuster.fit(candidates, 600);

        assertThat(result).hasSize(RunningTimeAdjuster.MAX_TRACKS);
    }

    private static int minutes(int minutes) {
        return minutes * 60_000;
    }

    private static Track track(Long itunesTrackId, Integer trackTimeMillis) {
        return Track.builder()
                .itunesTrackId(itunesTrackId).title("곡" + itunesTrackId).artist("가수").genre("K-Pop")
                .trackTimeMillis(trackTimeMillis).build();
    }

    private static List<Long> ids(List<Track> tracks) {
        return tracks.stream().map(Track::getItunesTrackId).toList();
    }
}
