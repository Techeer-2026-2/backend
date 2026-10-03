package com.techeer.backend.domain.ranking.service;

import com.techeer.backend.domain.ranking.repository.RankingRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ranking.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class RankingJobs implements ApplicationRunner {
    private final RankingBatchService batchService;
    private final RankingRepository repository;
    private final Clock clock;
    private final MeterRegistry meters;

    @Override
    public void run(ApplicationArguments arguments) {
        if (arguments.containsOption("ranking.rebuild-hour")) {
            // 예: --ranking.rebuild-hour=2026-09-26T21:00:00+09:00
            execute(Instant.parse(arguments.getOptionValues("ranking.rebuild-hour").getFirst()));
        }
        catchUp();
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void catchUp() {
        Instant lastHour = clock.instant().truncatedTo(ChronoUnit.HOURS).minus(1, ChronoUnit.HOURS);
        Instant first = repository.latestBatch().map(batch -> batch.hour().plus(1, ChronoUnit.HOURS)).orElse(lastHour);
        Instant earliest = lastHour.minus(23, ChronoUnit.HOURS);
        if (first.isBefore(earliest)) {
            log.warn("랭킹 자동 복구 범위는 최근 24시간입니다. 이전 버킷은 수동 재집계가 필요합니다: {}", first);
            first = earliest;
        }
        for (Instant hour = first; !hour.isAfter(lastHour); hour = hour.plus(1, ChronoUnit.HOURS)) {
            if (!execute(hour)) {
                break;
            }
        }
    }

    public boolean execute(Instant hour) {
        long started = System.nanoTime();
        String status = "SUCCESS";
        String errorType = null;
        try {
            if (!batchService.rebuild(hour)) {
                status = "SKIPPED";
                return false;
            }
            return true;
        } catch (RuntimeException exception) {
            status = "FAILED";
            errorType = exception.getClass().getSimpleName();
            log.error("랭킹 배치 실패: {}", hour, exception);
            throw exception;
        } finally {
            long duration = System.nanoTime() - started;
            meters.timer("ranking.batch.duration", "status", status).record(duration, TimeUnit.NANOSECONDS);
            // 집계 트랜잭션 밖에서 실행되어 실패한 배치의 이력도 남는다.
            repository.recordAttempt(hour, clock.instant(), status, duration / 1_000_000, errorType);
            log.info("랭킹 배치 hour={} status={} durationMs={}", hour, status, duration / 1_000_000);
        }
    }
}
