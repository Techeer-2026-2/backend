package com.techeer.backend.domain.music.seed;

import com.techeer.backend.domain.music.seed.TrackMoodImporter.Row;
import com.techeer.backend.domain.playlist.entity.Track;
import com.techeer.backend.domain.playlist.entity.TrackMood;
import com.techeer.backend.domain.playlist.entity.TrackMoodScore;
import com.techeer.backend.domain.playlist.entity.TrackMoodSource;
import com.techeer.backend.domain.playlist.repository.TrackMoodScoreRepository;
import com.techeer.backend.domain.playlist.repository.TrackRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 한 묶음을 한 트랜잭션으로 쓴다. (같은 클래스 안에서 부르면 @Transactional 이 적용되지 않아 별도 빈으로 분리) */
@Component
@RequiredArgsConstructor
class TrackMoodChunkWriter {

    private final TrackRepository trackRepository;
    private final TrackMoodScoreRepository moodScoreRepository;

    record Counts(int created, int scores) {
    }

    @Transactional
    Counts write(List<Row> rows) {
        int created = 0;
        int scores = 0;
        for (Row r : rows) {
            Track track = trackRepository.findByItunesTrackId(r.itunesTrackId()).orElse(null);
            if (track == null) {
                track = trackRepository.save(Track.builder()
                        .itunesTrackId(r.itunesTrackId()).title(r.title()).artist(r.artist())
                        .collectionName(r.collectionName()).genre(r.genre()).artworkUrl(r.artworkUrl())
                        .previewUrl(r.previewUrl()).trackTimeMillis(r.trackTimeMillis()).build());
                created++;
            }
            // 파일에 적힌 분위기가 최종 값이다: 있으면 갱신, 없으면 추가, 파일에 없는 기존 분위기는 지운다.
            List<TrackMoodScore> existing = moodScoreRepository.findAllByTrackId(track.getId());
            for (TrackMoodScore old : existing) {
                if (!r.moods().containsKey(old.getMood())) {
                    moodScoreRepository.delete(old);
                }
            }
            for (Map.Entry<TrackMood, Double> e : r.moods().entrySet()) {
                TrackMoodScore found = existing.stream()
                        .filter(s -> s.getMood() == e.getKey()).findFirst().orElse(null);
                if (found == null) {
                    moodScoreRepository.save(
                            TrackMoodScore.create(track, e.getKey(), e.getValue(), TrackMoodSource.SCORED));
                } else {
                    found.rescore(e.getValue(), TrackMoodSource.SCORED);
                }
                scores++;
            }
        }
        return new Counts(created, scores);
    }
}
