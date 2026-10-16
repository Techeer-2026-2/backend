package com.techeer.backend.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class TimeBandTest {

    @Test
    void 시각을_다섯_시간대로_나눈다_경계_포함() {
        assertThat(TimeBand.of(LocalTime.of(0, 0))).isEqualTo(TimeBand.DAWN);
        assertThat(TimeBand.of(LocalTime.of(5, 59))).isEqualTo(TimeBand.DAWN);
        assertThat(TimeBand.of(LocalTime.of(6, 0))).isEqualTo(TimeBand.MORNING_RUSH);
        assertThat(TimeBand.of(LocalTime.of(9, 59))).isEqualTo(TimeBand.MORNING_RUSH);
        assertThat(TimeBand.of(LocalTime.of(10, 0))).isEqualTo(TimeBand.DAYTIME);
        assertThat(TimeBand.of(LocalTime.of(16, 59))).isEqualTo(TimeBand.DAYTIME);
        assertThat(TimeBand.of(LocalTime.of(17, 0))).isEqualTo(TimeBand.EVENING_RUSH);
        assertThat(TimeBand.of(LocalTime.of(20, 59))).isEqualTo(TimeBand.EVENING_RUSH);
        assertThat(TimeBand.of(LocalTime.of(21, 0))).isEqualTo(TimeBand.NIGHT);
        assertThat(TimeBand.of(LocalTime.of(23, 59))).isEqualTo(TimeBand.NIGHT);
    }
}
