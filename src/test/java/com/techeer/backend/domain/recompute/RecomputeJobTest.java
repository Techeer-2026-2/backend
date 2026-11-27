package com.techeer.backend.domain.recompute;

import static com.techeer.backend.domain.recompute.RecomputeSql.EventKind.CLICK;
import static com.techeer.backend.domain.recompute.RecomputeSql.EventKind.IMPRESSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class RecomputeJobTest {

    @Test
    void retriesFailedDateOnceThenContinuesOnlyThroughRequestedDates() {
        LocalDate from = LocalDate.of(2026, 11, 20);
        LocalDate to = from.plusDays(1);
        var sql = mock(RecomputeSql.class);
        var logs = mock(PipelineLogRepository.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(logs).serializeJobs(any());
        when(logs.start(from)).thenReturn(1L);
        when(logs.start(to)).thenReturn(2L);
        when(sql.archive(any(), eq(CLICK))).thenReturn(new RecomputeSql.ArchiveCounts(1, 3, 2));
        when(sql.archive(any(), eq(IMPRESSION))).thenReturn(new RecomputeSql.ArchiveCounts(1, 1, 1));
        when(sql.daily(to, CLICK)).thenReturn(3L, 2L);
        when(sql.daily(to, IMPRESSION)).thenReturn(1L);
        doThrow(new IllegalStateException("Test replace failure")).when(sql).replace(from, IMPRESSION);

        var command = new RecomputeCommand(new RecomputeJob(sql, logs), null);
        int result = command.execute(new DefaultApplicationArguments(
                "--recompute.from=" + from, "--recompute.to=" + to));

        assertThat(result).isEqualTo(1);
        verify(logs, times(2)).start(any());
        verify(sql, times(2)).replace(from, CLICK);
        verify(sql, times(2)).replace(from, IMPRESSION);
        verify(logs).fail(1, "IllegalStateException: Test replace failure");
        verify(logs).complete(2, 2, 2, 1, 1, 4, 3);
    }
}
