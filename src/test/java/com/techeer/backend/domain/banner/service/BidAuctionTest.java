package com.techeer.backend.domain.banner.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.techeer.backend.domain.campaign.entity.Campaign;
import com.techeer.backend.domain.campaign.entity.CampaignStatus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BidAuctionTest {

    private final Campaign high = campaign(50);
    private final Campaign mid = campaign(30);
    private final Campaign low = campaign(10);

    @Test
    @DisplayName("차감액은 나보다 낮은 입찰가 중 최고 + 1원이고 내 입찰가를 넘지 않는다")
    void chargesNextBidPlusOne() {
        List<Campaign> candidates = List.of(high, mid, low);

        assertThat(BidAuction.charge(high, candidates)).isEqualTo(31);
        assertThat(BidAuction.charge(mid, candidates)).isEqualTo(11);
        assertThat(BidAuction.charge(campaign(31), List.of(campaign(31), campaign(30)))).isEqualTo(31);
    }

    @Test
    @DisplayName("나보다 낮은 후보가 없으면 내 입찰가를 차감한다")
    void chargesOwnBidWithoutLowerBid() {
        assertThat(BidAuction.charge(low, List.of(high, mid, low))).isEqualTo(10);
        assertThat(BidAuction.charge(high, List.of(high))).isEqualTo(50);
        Campaign tie = campaign(50);
        assertThat(BidAuction.charge(high, List.of(high, tie))).isEqualTo(50);
    }

    @Test
    @DisplayName("입찰가에 비례한 구간으로 고른다")
    void picksByBidWeight() {
        List<Campaign> candidates = List.of(high, mid, low); // 합 90: [0,50) high, [50,80) mid, [80,90) low

        assertThat(BidAuction.pick(candidates, 0.0)).isSameAs(high);
        assertThat(BidAuction.pick(candidates, 49.9 / 90)).isSameAs(high);
        assertThat(BidAuction.pick(candidates, 50.0 / 90)).isSameAs(mid);
        assertThat(BidAuction.pick(candidates, 80.0 / 90)).isSameAs(low);
        assertThat(BidAuction.pick(candidates, 0.999999)).isSameAs(low);
    }

    private static Campaign campaign(long bidPrice) {
        return Campaign.builder()
                .userId(1L).title("t").body("b")
                .ageGroups(Set.of("20s")).genders(Set.of("all"))
                .status(CampaignStatus.ACTIVE)
                .totalBudget(100_000).bidPrice(bidPrice).targetClicks(1)
                .build();
    }
}
