package com.techeer.backend.domain.music.seed;

import com.techeer.backend.domain.music.dto.TrackCandidateResponse;
import com.techeer.backend.domain.music.seed.TrackSeedCatalog.Query;
import com.techeer.backend.domain.music.service.MusicSearchService;
import com.techeer.backend.domain.playlist.entity.TrackMood;
import com.techeer.backend.global.exception.BusinessException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 분위기별 검색어로 iTunes 에서 곡을 모아 tracks 에 저장한다. 서버를 켤 때 한 번 돌리는 용도다.
 *
 * <p>iTunes Search API 는 호출이 잦으면 막힌다(대략 분당 20회). 그래서
 * ① 검색 사이에 간격을 두고 ② 실패하면 오래 쉬었다가 몇 번만 다시 시도하고
 * ③ 연속으로 실패하면 막힌 것으로 보고 중단한다. 이미 저장된 곡은 다시 만들지 않으므로 다시 돌려도 안전하다.
 */
@Slf4j
@Service
public class TrackSeedService {

    public record Summary(int queries, int failedQueries, int created, int tagged, int skipped, boolean aborted) {
    }

    private final MusicSearchService musicSearchService;
    private final TrackSeedWriter writer;
    private final long delayMs;
    private final long retryWaitMs;
    private final int maxRetries;
    private final int maxConsecutiveFailures;

    public TrackSeedService(
            MusicSearchService musicSearchService,
            TrackSeedWriter writer,
            @Value("${track-seed.delay-ms:4000}") long delayMs,
            @Value("${track-seed.retry-wait-ms:60000}") long retryWaitMs,
            @Value("${track-seed.max-retries:2}") int maxRetries,
            @Value("${track-seed.max-consecutive-failures:3}") int maxConsecutiveFailures) {
        this.musicSearchService = musicSearchService;
        this.writer = writer;
        this.delayMs = delayMs;
        this.retryWaitMs = retryWaitMs;
        this.maxRetries = maxRetries;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
    }

    public Summary run() {
        int queries = 0;
        int failedQueries = 0;
        int created = 0;
        int tagged = 0;
        int skipped = 0;
        int consecutiveFailures = 0;
        boolean aborted = false;

        outer:
        for (TrackMood mood : TrackMood.values()) {
            for (Query query : TrackSeedCatalog.QUERIES.getOrDefault(mood, List.of())) {
                queries++;
                try {
                    List<TrackCandidateResponse> found = searchWithRetry(query);
                    List<TrackCandidateResponse> usable = found.stream()
                            .filter(c -> query.accepts(c.genre()))
                            .filter(c -> c.previewUrl() != null && c.trackTimeMillis() != null)
                            .toList();
                    TrackSeedWriter.SaveResult saved = writer.save(mood, usable);
                    created += saved.created();
                    tagged += saved.tagged();
                    skipped += saved.skipped() + (found.size() - usable.size());
                    consecutiveFailures = 0;
                    log.info("[track-seed] {} '{}' → 받음 {}, 사용 {}, 신규 {}, 태그만 {}",
                            mood, query.term(), found.size(), usable.size(), saved.created(), saved.tagged());
                } catch (BusinessException e) {
                    failedQueries++;
                    consecutiveFailures++;
                    log.warn("[track-seed] {} '{}' 실패 ({}회 연속)", mood, query.term(), consecutiveFailures);
                    if (consecutiveFailures >= maxConsecutiveFailures) {
                        log.error("[track-seed] 연속 {}회 실패 — iTunes 에서 막힌 것으로 보고 중단합니다. 잠시 뒤 다시 실행하세요.",
                                consecutiveFailures);
                        aborted = true;
                        break outer;
                    }
                }
                if (!pause(delayMs)) {
                    aborted = true;
                    break outer;
                }
            }
        }
        return new Summary(queries, failedQueries, created, tagged, skipped, aborted);
    }

    private List<TrackCandidateResponse> searchWithRetry(Query query) {
        BusinessException last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return musicSearchService.search(query.term());
            } catch (BusinessException e) {
                last = e;
                if (attempt < maxRetries && !pause(retryWaitMs)) {
                    break;
                }
            }
        }
        throw last;
    }

    /** @return 정상적으로 쉬었으면 true, 중단 요청(인터럽트)을 받았으면 false */
    private boolean pause(long millis) {
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
