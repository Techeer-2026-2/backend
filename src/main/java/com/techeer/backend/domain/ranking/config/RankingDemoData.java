package com.techeer.backend.domain.ranking.config;

import com.techeer.backend.domain.ranking.dto.RankingDtos.EventRequest;
import com.techeer.backend.domain.ranking.dto.RankingDtos.EventType;
import com.techeer.backend.domain.ranking.dto.RankingDtos.LocationInput;
import com.techeer.backend.domain.ranking.dto.RankingDtos.LocationRequest;
import com.techeer.backend.domain.ranking.dto.RankingDtos.StartRequest;
import com.techeer.backend.domain.ranking.dto.RankingDtos.TrackInput;
import com.techeer.backend.domain.ranking.service.PlaybackService;
import com.techeer.backend.domain.ranking.service.RankingBatchService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 명시적으로 ranking-demo 프로필을 선택한 개발 DB에만 가상 청취를 생성한다. */
@Component
@Profile("ranking-demo")
@Order(0)
@RequiredArgsConstructor
public class RankingDemoData implements ApplicationRunner {
    private static final int TRACK_COUNT = 50;
    private static final int EVENT_COUNT = 1_000;
    private static final String[] ARTISTS = {
            "Morning Club", "유리창", "Seoul Tape", "Platform 2", "느린 걸음",
            "Night Bus", "City Loop", "Han River FM", "Commuter", "Metro Blue"
    };
    private static final String[] MOODS = {"새벽", "아침", "한낮", "노을", "밤"};
    private static final String[] SCENES = {
            "첫차의 리듬", "창가에 앉아", "도시의 숨", "한 정거장 더", "이어폰 속 산책",
            "집으로 가는 길", "신호등 앞에서", "강변 드라이브", "플랫폼의 바람", "골목의 불빛"
    };
    private static final GpsPoint[] SEONGSU_POINTS = {
            new GpsPoint(37.5442, 127.0561), new GpsPoint(37.5448, 127.0555),
            new GpsPoint(37.5437, 127.0568), new GpsPoint(37.5451, 127.0570),
            new GpsPoint(37.5439, 127.0549)
    };
    private static final GpsPoint[] GANGNAM_POINTS = {
            new GpsPoint(37.4979, 127.0276), new GpsPoint(37.4985, 127.0282),
            new GpsPoint(37.4973, 127.0269)
    };
    private static final GpsPoint[] BUSAN_POINTS = {
            new GpsPoint(35.1796, 129.0756), new GpsPoint(35.1802, 129.0763),
            new GpsPoint(35.1790, 129.0749)
    };

    private final PlaybackService playback;
    private final RankingBatchService batch;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        Instant hour = clock.instant().truncatedTo(ChronoUnit.HOURS).minus(1, ChronoUnit.HOURS);
        for (int track = 0; track < TRACK_COUNT; track++) {
            playback.saveTrack(trackId(track), new TrackInput(title(track), ARTISTS[track % ARTISTS.length], null, null));
        }
        for (int event = 0; event < EVENT_COUNT; event++) {
            int track = event % TRACK_COUNT;
            GpsPoint point = gpsPoint(event);
            Instant startedAt = hour.plusSeconds(60 + event * 3L);
            String key = "dataset-1000-gps:" + hour + ":" + event;
            UUID sessionId = id("session:" + key);
            LocationInput location = new LocationInput(point.lat(), point.lng(), 5.0 + event % 15,
                    startedAt.minusSeconds(event % 10));
            long userId = 10_000_000L + event;
            playback.updateLocation(new LocationRequest(userId, location));
            playback.start(new StartRequest(sessionId, userId, trackId(track), startedAt, null));
            playback.record(new EventRequest(id("event:" + key), sessionId, EventType.TRACK_COMPLETED,
                    1.0, startedAt.plusSeconds(120)));
        }
        batch.rebuild(hour);
    }

    private String trackId(int track) {
        return "demo-%02d".formatted(track + 1);
    }

    private String title(int track) {
        return "%s · %s".formatted(MOODS[track / SCENES.length], SCENES[track % SCENES.length]);
    }

    private GpsPoint gpsPoint(int event) {
        if (event < 800) {
            return SEONGSU_POINTS[event % SEONGSU_POINTS.length];
        }
        if (event < 920) {
            return GANGNAM_POINTS[(event - 800) % GANGNAM_POINTS.length];
        }
        return BUSAN_POINTS[(event - 920) % BUSAN_POINTS.length];
    }

    private UUID id(String input) {
        return UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8));
    }

    private record GpsPoint(double lat, double lng) {
    }
}
