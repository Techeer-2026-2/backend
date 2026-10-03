package com.techeer.backend.domain.ranking.service;

import com.techeer.backend.domain.ranking.entity.RankingModels.Chart;
import com.techeer.backend.domain.ranking.entity.RankingModels.Completion;
import com.techeer.backend.domain.ranking.repository.RankingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RankingBatchService {
    private final RankingRepository repository;
    private final RankingCalculator calculator;
    private final Clock clock;

    /** 실패 시 기존 시간 차트를 보존한다. 다른 서버가 같은 시간을 처리 중이면 건너뛴다. */
    @Transactional
    public boolean rebuild(Instant hour) {
        if (!hour.equals(hour.truncatedTo(ChronoUnit.HOURS))
                || hour.plus(1, ChronoUnit.HOURS).isAfter(clock.instant())) {
            throw new IllegalArgumentException("종료된 정각 시간 버킷만 집계할 수 있습니다");
        }
        if (!repository.lockHour(hour)) {
            return false;
        }
        long started = System.nanoTime();
        List<Completion> events = repository.completions(hour);
        List<Chart> charts = calculator.calculate(events);
        repository.replaceCharts(hour, clock.instant(), events.size(),
                (System.nanoTime() - started) / 1_000_000, charts);
        return true;
    }
}
