package com.techeer.backend.domain.music.seed;

import com.techeer.backend.domain.music.dto.TrackCandidateResponse;
import com.techeer.backend.domain.playlist.entity.Track;
import com.techeer.backend.domain.playlist.entity.TrackMood;
import com.techeer.backend.domain.playlist.entity.TrackMoodScore;
import com.techeer.backend.domain.playlist.entity.TrackMoodSource;
import com.techeer.backend.domain.playlist.repository.TrackMoodScoreRepository;
import com.techeer.backend.domain.playlist.repository.TrackRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 검색 결과 한 묶음을 한 트랜잭션으로 저장한다. (외부 호출을 기다리는 동안 트랜잭션을 열어 두지 않으려고 분리) */
@Component
@RequiredArgsConstructor
public class TrackSeedWriter {

    private final TrackRepository trackRepository;
    private final TrackMoodScoreRepository moodScoreRepository;

    /** created: 새로 저장한 곡, tagged: 이미 있던 곡에 새 분위기를 붙인 수, skipped: 이미 있던 곡·분위기 */
    public record SaveResult(int created, int tagged, int skipped) {
    }

    @Transactional
    public SaveResult save(TrackMood mood, List<TrackCandidateResponse> candidates) {
        int created = 0;
        int tagged = 0;
        int skipped = 0;
        for (TrackCandidateResponse candidate : candidates) {
            if (candidate.itunesTrackId() == null) {
                skipped++;
                continue;
            }
            Track track = trackRepository.findByItunesTrackId(candidate.itunesTrackId()).orElse(null);
            boolean isNew = track == null;
            if (isNew) {
                track = trackRepository.save(Track.builder()
                        .itunesTrackId(candidate.itunesTrackId())
                        .title(candidate.trackName())
                        .artist(candidate.artistName())
                        .genre(candidate.genre())
                        .collectionName(candidate.collectionName())
                        .artworkUrl(candidate.artworkUrl())
                        .previewUrl(candidate.previewUrl())
                        .trackTimeMillis(candidate.trackTimeMillis())
                        .build());
                created++;
            }
            if (moodScoreRepository.findByTrackIdAndMood(track.getId(), mood).isPresent()) {
                skipped++;
                continue;
            }
            moodScoreRepository.save(TrackMoodScore.create(track, mood, 1.0, TrackMoodSource.KEYWORD));
            if (!isNew) {
                tagged++;
            }
        }
        return new SaveResult(created, tagged, skipped);
    }
}
