package com.techeer.backend.domain.music.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.playlist.entity.Track;
import com.techeer.backend.domain.playlist.entity.TrackMood;
import com.techeer.backend.domain.playlist.entity.TrackMoodScore;
import com.techeer.backend.domain.playlist.entity.TrackMoodSource;
import com.techeer.backend.domain.playlist.repository.TrackMoodScoreRepository;
import com.techeer.backend.domain.playlist.repository.TrackRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TrackMoodImporterTest {

    private static final String HEADER =
            "itunesTrackId,title,artist,collectionName,genre,artworkUrl,previewUrl,trackTimeMillis,moods\n";

    @Autowired
    TrackMoodImporter importer;

    @Autowired
    TrackRepository trackRepository;

    @Autowired
    TrackMoodScoreRepository moodScoreRepository;

    @TempDir
    Path dir;

    @BeforeEach
    void clean() {
        moodScoreRepository.deleteAll();
        trackRepository.deleteAll();
    }

    private Path csv(String body) throws IOException {
        Path file = dir.resolve("track-moods.csv");
        Files.writeString(file, HEADER + body, StandardCharsets.UTF_8);
        return file;
    }

    private TrackMoodScore score(long itunesTrackId, TrackMood mood) {
        Track t = trackRepository.findByItunesTrackId(itunesTrackId).orElseThrow();
        return moodScoreRepository.findByTrackIdAndMood(t.getId(), mood).orElse(null);
    }

    @Test
    void 곡과_분위기_점수를_저장한다() throws IOException {
        TrackMoodImporter.Result result = importer.importFile(csv(
                "1,\"비, 그리고\",가수,앨범,K-Pop,art,preview,180000,GLOOMY:0.80|CALM:0.40\n"));

        assertThat(result.tracksCreated()).isEqualTo(1);
        assertThat(result.scoresWritten()).isEqualTo(2);
        assertThat(trackRepository.findByItunesTrackId(1L).orElseThrow().getTitle()).isEqualTo("비, 그리고");
        assertThat(score(1, TrackMood.GLOOMY).getScore()).isEqualTo(0.80);
        assertThat(score(1, TrackMood.GLOOMY).getSource()).isEqualTo(TrackMoodSource.SCORED);
    }

    @Test
    void 다시_돌려도_같은_결과이고_파일에_없는_분위기는_지운다() throws IOException {
        importer.importFile(csv("1,곡,가수,앨범,Pop,art,preview,180000,GLOOMY:0.80|CALM:0.40\n"));

        importer.importFile(csv("1,곡,가수,앨범,Pop,art,preview,180000,GLOOMY:0.55\n"));

        assertThat(trackRepository.count()).isEqualTo(1);
        assertThat(moodScoreRepository.count()).isEqualTo(1);
        assertThat(score(1, TrackMood.GLOOMY).getScore()).isEqualTo(0.55);
        assertThat(score(1, TrackMood.CALM)).isNull();
    }

    @Test
    void 잘못된_줄은_건너뛰고_나머지는_저장한다() throws IOException {
        TrackMoodImporter.Result result = importer.importFile(csv(
                "1,곡1,가수,앨범,Pop,art,preview,180000,NOPE:0.5\n"
                        + "2,곡2,가수,앨범,Pop,art,preview,180000,GLOOMY:1.5\n"
                        + "x,곡3,가수,앨범,Pop,art,preview,180000,GLOOMY:0.5\n"
                        + "4,곡4,가수,앨범,Pop,art,preview,180000,UPBEAT:0.70\n"));

        assertThat(result.badRows()).isEqualTo(3);
        assertThat(trackRepository.count()).isEqualTo(1);
        assertThat(score(4, TrackMood.UPBEAT)).isNotNull();
    }

    @Test
    void 헤더가_다르면_아무것도_저장하지_않고_실패한다() throws IOException {
        Path file = dir.resolve("bad.csv");
        Files.writeString(file, "id,name\n1,x\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> importer.importFile(file)).isInstanceOf(IllegalArgumentException.class);
        assertThat(trackRepository.count()).isZero();
    }
}
