package com.techeer.backend.domain.ranking.config;

import com.techeer.backend.domain.ranking.dto.RankingDtos.EventRequest;
import com.techeer.backend.domain.ranking.dto.RankingDtos.EventType;
import com.techeer.backend.domain.ranking.dto.RankingDtos.LocationInput;
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
    private final PlaybackService playback;
    private final RankingBatchService batch;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        String[] titles = {"첫차의 리듬", "창가에 앉아", "도시의 아침", "한 정거장 더", "이어폰 속 산책", "집으로 가는 길"};
        String[] artists = {"Morning Club", "유리창", "Seoul Tape", "Platform 2", "느린 걸음", "Night Bus"};
        int[] listeners = {24, 19, 12, 8, 5, 3};
        Instant hour = clock.instant().truncatedTo(ChronoUnit.HOURS).minus(1, ChronoUnit.HOURS);
        for (int track = 0; track < titles.length; track++) {
            String trackId = "demo-" + track;
            playback.saveTrack(trackId, new TrackInput(titles[track], artists[track], null, null));
            for (int user = 1; user <= listeners[track]; user++) {
                String key = hour + ":" + track + ":" + user;
                Instant startedAt = hour.plusSeconds(60 + track * 400L + user);
                UUID sessionId = id("session:" + key);
                playback.start(new StartRequest(sessionId, 9_000_000L + user, trackId, startedAt,
                        user <= 20 ? new LocationInput(37.5442, 127.0561, 10.0, startedAt) : null));
                playback.record(new EventRequest(id("event:" + key), sessionId, EventType.TRACK_COMPLETED,
                        1.0, startedAt.plusSeconds(180)));
            }
        }
        batch.rebuild(hour);
    }

    private UUID id(String input) {
        return UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8));
    }
}
