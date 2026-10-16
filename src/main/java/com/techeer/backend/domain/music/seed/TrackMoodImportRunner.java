package com.techeer.backend.domain.music.seed;

import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** {@code track-import} 프로필로 켰을 때만 track-moods.csv 를 DB 에 반영하고 종료한다. */
@Slf4j
@Component
@Profile("track-import")
@RequiredArgsConstructor
public class TrackMoodImportRunner implements ApplicationRunner {

    private final TrackMoodImporter importer;
    private final ApplicationContext context;

    @Value("${track-import.file:build/track-moods.csv}")
    private String file;

    @Override
    public void run(ApplicationArguments args) {
        TrackMoodImporter.Result result = importer.importFile(Path.of(file));
        log.info("[track-import] 끝 — {}", result);
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
