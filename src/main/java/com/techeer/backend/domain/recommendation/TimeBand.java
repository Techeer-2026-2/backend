package com.techeer.backend.domain.recommendation;

import java.time.LocalTime;

/** 하루를 추천에 쓰는 5개 시간대로 나눈다. (한국 시간 기준, 호출하는 쪽이 Clock 으로 시각을 구해 넘긴다) */
public enum TimeBand {
    /** 0~5시 */
    DAWN,
    /** 6~9시 출근 러시 */
    MORNING_RUSH,
    /** 10~16시 */
    DAYTIME,
    /** 17~20시 퇴근 러시 */
    EVENING_RUSH,
    /** 21~23시 */
    NIGHT;

    public static TimeBand of(LocalTime time) {
        int hour = time.getHour();
        if (hour < 6) {
            return DAWN;
        }
        if (hour < 10) {
            return MORNING_RUSH;
        }
        if (hour < 17) {
            return DAYTIME;
        }
        if (hour < 21) {
            return EVENING_RUSH;
        }
        return NIGHT;
    }
}
