package com.techeer.backend.domain.music.seed;

import com.techeer.backend.domain.playlist.entity.Track;
import com.techeer.backend.domain.playlist.entity.TrackMoodScore;
import com.techeer.backend.domain.playlist.entity.TrackMoodSource;
import com.techeer.backend.domain.playlist.repository.TrackMoodScoreRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * {@code track-seed} 프로필로 서버를 켰을 때만 곡 수집을 한 번 실행하고, 확인용 CSV 를 쓴 뒤 종료한다.
 * 주소(API)로 열지 않는 이유: 관리자 구분이 아직 없어 누구나 호출할 수 있게 되기 때문이다.
 */
@Slf4j
@Component
@Profile("track-seed")
@RequiredArgsConstructor
public class TrackSeedRunner implements ApplicationRunner {

    private final TrackSeedService seedService;
    private final TrackMoodScoreRepository moodScoreRepository;
    private final ApplicationContext context;

    @Value("${track-seed.report-path:build/track-seed-report.csv}")
    private String reportPath;

    @Override
    public void run(ApplicationArguments args) {
        TrackSeedService.Summary summary = seedService.run();
        log.info("[track-seed] 끝 — {}", summary);
        writeReport(Path.of(reportPath));
        System.exit(SpringApplication.exit(context, () -> summary.aborted() ? 1 : 0));
    }

    private void writeReport(Path path) {
        // 곡마다 "검색어로 찾은 분위기들"을 모아 한 줄로 쓴다. 다음 단계(점수 계산 스크립트)의 입력이다.
        Map<Track, List<String>> moodsByTrack = new LinkedHashMap<>();
        for (TrackMoodScore s : moodScoreRepository.findAllWithTrackBySource(TrackMoodSource.KEYWORD)) {
            moodsByTrack.computeIfAbsent(s.getTrack(), t -> new ArrayList<>()).add(s.getMood().name());
        }
        List<String> lines = new ArrayList<>();
        lines.add("itunesTrackId,title,artist,collectionName,genre,artworkUrl,previewUrl,trackTimeMillis,keywordMoods");
        moodsByTrack.forEach((t, moods) -> lines.add(String.join(",",
                String.valueOf(t.getItunesTrackId()), csv(t.getTitle()), csv(t.getArtist()),
                csv(t.getCollectionName()), csv(t.getGenre()), csv(t.getArtworkUrl()), csv(t.getPreviewUrl()),
                String.valueOf(t.getTrackTimeMillis()), csv(String.join("|", moods)))));
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.write(path, lines, StandardCharsets.UTF_8);
            log.info("[track-seed] 수집한 {}곡 → {}", lines.size() - 1, path.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
