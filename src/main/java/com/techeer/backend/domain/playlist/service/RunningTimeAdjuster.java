package com.techeer.backend.domain.playlist.service;

import com.techeer.backend.domain.music.dto.TrackCandidateResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 추천 곡 후보에서 곡을 골라 총 재생시간을 통근 소요시간에 맞춘다 (#90).
 *
 * <p>후보는 추천 우선순위 순서대로 받는다고 가정한다. 앞에서부터 목표 시간을 넘지 않는 곡을 담고,
 * 남은 공백보다 목표에 더 가깝게 만드는 곡이 있으면 하나를 더 붙인다. 그래서 결과는
 * 목표보다 조금 짧거나, 모자란 시간보다 덜 넘친다.
 */
@Component
public class RunningTimeAdjuster {

    /** 플레이리스트 곡 추가 API 의 최대 곡 수(#56)와 맞춘다. */
    static final int MAX_TRACKS = 100;

    public List<TrackCandidateResponse> fit(List<TrackCandidateResponse> candidates, int targetMinutes) {
        long targetMillis = targetMinutes * 60_000L;
        List<TrackCandidateResponse> selected = new ArrayList<>();
        if (targetMillis <= 0) {
            return selected;
        }

        List<TrackCandidateResponse> skipped = new ArrayList<>();
        Set<Long> seenTrackIds = new HashSet<>();
        long totalMillis = 0;
        for (TrackCandidateResponse candidate : candidates) {
            if (selected.size() == MAX_TRACKS) {
                return selected;
            }
            if (!isUsable(candidate) || !seenTrackIds.add(candidate.itunesTrackId())) {
                continue;
            }
            if (totalMillis + candidate.trackTimeMillis() <= targetMillis) {
                selected.add(candidate);
                totalMillis += candidate.trackTimeMillis();
            } else {
                skipped.add(candidate);
            }
        }

        if (selected.size() == MAX_TRACKS) {
            return selected;
        }
        long gapMillis = targetMillis - totalMillis;
        skipped.stream()
                .filter(candidate -> overshoot(candidate, gapMillis) < gapMillis)
                .min(Comparator.comparingLong(candidate -> overshoot(candidate, gapMillis)))
                .ifPresent(selected::add);
        return selected;
    }

    /** 곡 ID 가 없으면 플레이리스트에 담을 수 없고, 재생시간을 모르면 시간을 맞출 수 없다. */
    private static boolean isUsable(TrackCandidateResponse candidate) {
        return candidate.itunesTrackId() != null
                && candidate.trackTimeMillis() != null
                && candidate.trackTimeMillis() > 0;
    }

    /** 공백을 채우고 남는(넘치는) 시간. skipped 곡은 모두 공백보다 길어서 항상 0 이상이다. */
    private static long overshoot(TrackCandidateResponse candidate, long gapMillis) {
        return candidate.trackTimeMillis() - gapMillis;
    }
}
