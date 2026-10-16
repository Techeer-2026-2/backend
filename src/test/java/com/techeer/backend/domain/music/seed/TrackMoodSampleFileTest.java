package com.techeer.backend.domain.music.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.playlist.entity.TrackMood;
import com.techeer.backend.domain.playlist.entity.TrackMoodScore;
import com.techeer.backend.domain.playlist.repository.TrackMoodScoreRepository;
import com.techeer.backend.domain.playlist.repository.TrackRepository;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** 레포에 들어 있는 로컬 시험용 샘플(tools/track-moods/sample)이 실제로 적재되는지 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TrackMoodSampleFileTest {

    private static final Path SAMPLE = Path.of("tools/track-moods/sample/track-moods-sample.csv");

    @Autowired
    TrackMoodImporter importer;

    @Autowired
    TrackRepository trackRepository;

    @Autowired
    TrackMoodScoreRepository moodScoreRepository;

    @BeforeEach
    void clean() {
        moodScoreRepository.deleteAll();
        trackRepository.deleteAll();
    }

    @Test
    void 샘플_파일은_잘못된_줄_없이_적재되고_여덟_분위기를_모두_담는다() {
        TrackMoodImporter.Result result = importer.importFile(SAMPLE);

        assertThat(result.badRows()).isZero();
        assertThat(result.rows()).isEqualTo(240);
        assertThat(trackRepository.count()).isEqualTo(240);
        Set<TrackMood> moods = EnumSet.noneOf(TrackMood.class);
        List<TrackMoodScore> all = moodScoreRepository.findAll();
        all.forEach(s -> moods.add(s.getMood()));
        assertThat(moods).containsExactlyInAnyOrder(TrackMood.values());
    }

    @Test
    void 샘플을_두_번_적재해도_결과가_같다() {
        importer.importFile(SAMPLE);
        long scores = moodScoreRepository.count();

        TrackMoodImporter.Result second = importer.importFile(SAMPLE);

        assertThat(second.tracksCreated()).isZero();
        assertThat(moodScoreRepository.count()).isEqualTo(scores);
    }
}
