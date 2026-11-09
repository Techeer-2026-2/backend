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
import org.springframework.scheduling.annotation.Scheduled;
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
            playback.saveTrack(trackId(track), new TrackInput(title(track), ARTISTS[track % ARTISTS.length], null, null,
                    artworkUrl(track), trackTimeMillis(track)));
        }
        Instant previousHour = hour.minus(1, ChronoUnit.HOURS);
        seedHour(previousHour);
        seedHour(hour);
        batch.rebuild(previousHour);
        batch.rebuild(hour);
    }

    /**
     * 서버를 켠 뒤에도 매시간 차트가 비지 않도록, 매시 59분에 진행 중인 시간의 청취를 채운다.
     * 정각의 RankingJobs.catchUp 이 이 시간을 집계한다. 같은 정각에 돌리면 두 작업의 실행 순서가 보장되지 않아 59분에 둔다.
     * 이벤트는 시간 시작 후 약 53분까지 분포하므로 59분에는 모두 과거 시각이다.
     */
    @Scheduled(cron = "0 59 * * * *", zone = "Asia/Seoul")
    public void seedCurrentHour() {
        seedHour(clock.instant().truncatedTo(ChronoUnit.HOURS));
    }

    // 이웃한 시간끼리 곡 배정을 한 칸씩 다르게 민다. 곡마다 청취자 수가 같아 동점이면 최근 완주 순으로 순위가 정해지므로,
    // 매시간 순위가 달라져 전체 차트에서 상승·하락 표시(rankChange)를 볼 수 있다.
    // ID 는 시간·순번으로 고정돼 같은 시간을 다시 채워도 중복 저장되지 않는다.
    private void seedHour(Instant hour) {
        int trackShift = (int) (hour.getEpochSecond() / 3600 % 2);
        for (int event = 0; event < EVENT_COUNT; event++) {
            int track = (event + trackShift) % TRACK_COUNT;
            GpsPoint point = gpsPoint(event);
            Instant startedAt = hour.plusSeconds(60 + event * 3L);
            // 밀기 값까지 키에 넣어, 시간마다 밀기 값이 달랐던 이전 데모 데이터와 세션 ID 가 겹쳐 409 가 나지 않게 한다.
            String key = "dataset-1000-gps:" + hour + ":" + trackShift + ":" + event;
            UUID sessionId = id("session:" + key);
            LocationInput location = new LocationInput(point.lat(), point.lng(), 5.0 + event % 15,
                    startedAt.minusSeconds(event % 10));
            long userId = 10_000_000L + event;
            playback.updateLocation(userId, new LocationRequest(location));
            playback.start(userId, new StartRequest(sessionId, trackId(track), startedAt, null));
            playback.record(userId, new EventRequest(id("event:" + key), sessionId, EventType.TRACK_COMPLETED,
                    1.0, startedAt.plusSeconds(120)));
        }
    }

    private String trackId(int track) {
        return "demo-%02d".formatted(track + 1);
    }

    private String title(int track) {
        return "%s · %s".formatted(MOODS[track / SCENES.length], SCENES[track % SCENES.length]);
    }

    // 곡마다 고정된 샘플 커버. 화면 확인용이라 실제 앨범 커버가 아니다
    private String artworkUrl(int track) {
        return "https://picsum.photos/seed/%s/300".formatted(trackId(track));
    }

    // 2분 30초 ~ 4분 57초 사이로 곡마다 다르게 둔다
    private int trackTimeMillis(int track) {
        return 150_000 + track * 3_000;
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
