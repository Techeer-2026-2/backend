package com.techeer.backend.domain.music.seed;

import com.techeer.backend.domain.playlist.entity.TrackMood;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 점수 계산 도구(tools/track-moods)가 만든 track-moods.csv 를 DB 에 반영한다.
 * iTunes 도 모델도 부르지 않으므로 운영 서버에서도 안전하게 쓸 수 있고, 여러 번 돌려도 같은 결과가 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackMoodImporter {

    private static final List<String> HEADER = List.of("itunesTrackId", "title", "artist", "collectionName", "genre",
            "artworkUrl", "previewUrl", "trackTimeMillis", "moods");
    private static final int CHUNK = 200;

    private final TrackMoodChunkWriter chunkWriter;

    public record Result(int rows, int tracksCreated, int scoresWritten, int badRows) {
    }

    record Row(long itunesTrackId, String title, String artist, String collectionName, String genre,
               String artworkUrl, String previewUrl, Integer trackTimeMillis, Map<TrackMood, Double> moods) {
    }

    public Result importFile(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("파일을 읽을 수 없습니다: " + file, e);
        }
        List<List<String>> lines = SimpleCsv.parse(text);
        if (lines.isEmpty() || !lines.get(0).equals(HEADER)) {
            throw new IllegalArgumentException("헤더가 올바르지 않습니다. 기대: " + HEADER);
        }
        List<Row> rows = new ArrayList<>();
        int bad = 0;
        for (List<String> cells : lines.subList(1, lines.size())) {
            Row row = toRow(cells);
            if (row == null) {
                bad++;
                log.warn("[track-import] 잘못된 줄을 건너뜁니다: {}", cells);
            } else {
                rows.add(row);
            }
        }
        int created = 0;
        int written = 0;
        for (int i = 0; i < rows.size(); i += CHUNK) {
            TrackMoodChunkWriter.Counts counts = chunkWriter.write(rows.subList(i, Math.min(i + CHUNK, rows.size())));
            created += counts.created();
            written += counts.scores();
        }
        return new Result(rows.size(), created, written, bad);
    }

    private Row toRow(List<String> c) {
        if (c.size() != HEADER.size()) {
            return null;
        }
        try {
            Map<TrackMood, Double> moods = new EnumMap<>(TrackMood.class);
            for (String part : c.get(8).split("\\|")) {
                if (part.isBlank()) {
                    continue;
                }
                String[] kv = part.split(":");
                double score = Double.parseDouble(kv[1]);
                if (Double.isNaN(score) || score < 0.0 || score > 1.0) {
                    return null;
                }
                moods.put(TrackMood.valueOf(kv[0]), score);
            }
            if (moods.isEmpty()) {
                return null;
            }
            return new Row(Long.parseLong(c.get(0)), c.get(1), c.get(2), blankToNull(c.get(3)), blankToNull(c.get(4)),
                    blankToNull(c.get(5)), blankToNull(c.get(6)),
                    blankToNull(c.get(7)) == null || c.get(7).equals("null") ? null : Integer.valueOf(c.get(7)), moods);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
