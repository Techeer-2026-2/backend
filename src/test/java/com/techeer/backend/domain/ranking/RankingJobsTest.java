package com.techeer.backend.domain.ranking;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.techeer.backend.domain.ranking.entity.RankingModels.Batch;
import com.techeer.backend.domain.ranking.repository.RankingRepository;
import com.techeer.backend.domain.ranking.service.RankingBatchService;
import com.techeer.backend.domain.ranking.service.RankingJobs;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RankingJobsTest {
    private static final Instant NOW = Instant.parse("2026-10-03T03:00:00Z");
    @Mock private RankingRepository repository;
    @Mock private RankingBatchService batch;
    private RankingJobs jobs;

    @BeforeEach
    void setup() {
        jobs = new RankingJobs(batch, repository, Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
    }

    @Test
    void startupRecoversMissingCompletedHoursChronologically() {
        when(repository.latestBatch()).thenReturn(Optional.of(new Batch(NOW.minusSeconds(10800), NOW)));
        when(batch.rebuild(any())).thenReturn(true);
        jobs.catchUp();
        var ordered = inOrder(batch);
        ordered.verify(batch).rebuild(NOW.minusSeconds(7200));
        ordered.verify(batch).rebuild(NOW.minusSeconds(3600));
        verify(batch, never()).rebuild(NOW);
    }

    @Test
    void lockedHourDoesNotAllowLaterHourToHideAGap() {
        when(repository.latestBatch()).thenReturn(Optional.of(new Batch(NOW.minusSeconds(10800), NOW)));
        when(batch.rebuild(NOW.minusSeconds(7200))).thenReturn(false);
        jobs.catchUp();
        verify(batch, never()).rebuild(NOW.minusSeconds(3600));
        verify(repository).recordAttempt(eq(NOW.minusSeconds(7200)), eq(NOW), eq("SKIPPED"), anyLong(), isNull());
    }

    @Test
    void failedBatchRecordsFailureAndStopsBeforePublishingNewerHours() {
        when(repository.latestBatch()).thenReturn(Optional.of(new Batch(NOW.minusSeconds(10800), NOW)));
        when(batch.rebuild(NOW.minusSeconds(7200))).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(jobs::catchUp).isInstanceOf(IllegalStateException.class);
        verify(repository).recordAttempt(eq(NOW.minusSeconds(7200)), eq(NOW), eq("FAILED"), anyLong(), eq("IllegalStateException"));
        verify(batch, never()).rebuild(NOW.minusSeconds(3600));
    }
}
