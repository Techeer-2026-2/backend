package com.techeer.backend.domain.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import com.techeer.backend.domain.campaign.dto.CampaignDetailResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CampaignDetailRateTest {

    @Test
    @DisplayName("소진율과 달성률은 백분율로 소수 둘째 자리에서 반올림한다")
    void percentRoundsToTwoDecimals() {
        assertThat(CampaignDetailResponse.percent(32000, 100000)).isEqualTo(32.0);
        assertThat(CampaignDetailResponse.percent(1, 3)).isEqualTo(33.33);
        assertThat(CampaignDetailResponse.percent(2, 3)).isEqualTo(66.67);
        assertThat(CampaignDetailResponse.percent(150, 100)).isEqualTo(150.0);
    }

    @Test
    @DisplayName("분모가 0 이면 0 (예산 0, 목표 0)")
    void zeroDenominatorIsZero() {
        assertThat(CampaignDetailResponse.percent(500, 0)).isEqualTo(0.0);
        assertThat(CampaignDetailResponse.percent(0, 0)).isEqualTo(0.0);
    }
}
