package com.techeer.backend.domain.recompute;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.scheduling.annotation.Scheduled;

final class RecomputeSchedule {

    private final RecomputeJob recompute;
    private final Clock clock;
    private final int days;

    RecomputeSchedule(RecomputeJob recompute, Clock clock, int days) {
        if (days < 1) {
            throw new IllegalArgumentException("recompute.window-days must be positive");
        }
        this.recompute = recompute;
        this.clock = clock;
        this.days = days;
    }

    @Scheduled(cron = "${recompute.cron:0 0 3 * * *}", zone = "Asia/Seoul")
    void recompute() {
        LocalDate today = LocalDate.now(clock);
        recompute.run(today.minusDays(days), today.minusDays(1));
    }
}
