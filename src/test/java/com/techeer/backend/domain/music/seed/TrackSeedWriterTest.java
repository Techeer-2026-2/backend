package com.techeer.backend.domain.music.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.music.dto.TrackCandidateResponse;
import com.techeer.backend.domain.playlist.entity.Track;
import com.techeer.backend.domain.playlist.entity.TrackMood;
import com.techeer.backend.domain.playlist.entity.TrackMoodScore;
import com.techeer.backend.domain.playlist.entity.TrackMoodSource;
import com.techeer.backend.domain.playlist.repository.TrackMoodScoreRepository;
import com.techeer.backend.domain.playlist.repository.TrackRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TrackSeedWriterTest {

    @Autowired
    TrackSeedWriter writer;

    @Autowired
    TrackRepository trackRepository;

    @Autowired
    TrackMoodScoreRepository moodScoreRepository;

    @BeforeEach
    void clean() {
        moodScoreRepository.deleteAll();
        trackRepository.deleteAll();
    }

    private static TrackCandidateResponse candidate(long id) {
        return new TrackCandidateResponse(id, "곡" + id, "가수", "앨범", "art", "preview", 180000, "Pop");
    }

    private long trackId(long itunesTrackId) {
        return trackRepository.findByItunesTrackId(itunesTrackId).orElseThrow().getId();
    }

    @Test
    void 새_곡은_분위기_점수와_함께_저장한다() {
        TrackSeedWriter.SaveResult result = writer.save(TrackMood.GLOOMY, List.of(candidate(1), candidate(2)));

        assertThat(result.created()).isEqualTo(2);
        TrackMoodScore score = moodScoreRepository.findByTrackIdAndMood(trackId(1), TrackMood.GLOOMY).orElseThrow();
        assertThat(score.getScore()).isEqualTo(1.0);
        assertThat(score.getSource()).isEqualTo(TrackMoodSource.KEYWORD);
    }

    @Test
    void 이미_있는_곡에는_새_분위기만_더하고_곡은_중복_저장하지_않는다() {
        writer.save(TrackMood.GLOOMY, List.of(candidate(1)));

        TrackSeedWriter.SaveResult result = writer.save(TrackMood.ROMANTIC, List.of(candidate(1)));

        assertThat(result.created()).isZero();
        assertThat(result.tagged()).isEqualTo(1);
        assertThat(trackRepository.count()).isEqualTo(1);
        assertThat(moodScoreRepository.count()).isEqualTo(2);
    }

    @Test
    void 같은_곡_같은_분위기를_다시_돌려도_그대로다() {
        writer.save(TrackMood.GLOOMY, List.of(candidate(1)));

        TrackSeedWriter.SaveResult again = writer.save(TrackMood.GLOOMY, List.of(candidate(1)));

        assertThat(again.created()).isZero();
        assertThat(again.tagged()).isZero();
        assertThat(again.skipped()).isEqualTo(1);
        assertThat(moodScoreRepository.count()).isEqualTo(1);
    }

    @Test
    void 사용자가_먼저_담아_분위기가_없던_곡에도_분위기를_붙인다() {
        trackRepository.save(Track.builder().itunesTrackId(1L).title("곡1").build());

        TrackSeedWriter.SaveResult result = writer.save(TrackMood.CALM, List.of(candidate(1)));

        assertThat(result.tagged()).isEqualTo(1);
        assertThat(trackRepository.count()).isEqualTo(1);
    }

    @Test
    void 같은_응답_안에_중복된_곡이_있어도_한_번만_저장한다() {
        TrackSeedWriter.SaveResult result = writer.save(TrackMood.UPBEAT, List.of(candidate(1), candidate(1)));

        assertThat(result.created()).isEqualTo(1);
        assertThat(trackRepository.count()).isEqualTo(1);
        assertThat(moodScoreRepository.count()).isEqualTo(1);
    }
}
