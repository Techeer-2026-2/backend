package com.techeer.backend.domain.banner.service;

import com.techeer.backend.domain.campaign.entity.Campaign;
import java.util.List;
import java.util.OptionalLong;

/**
 * 배너 후보 중 노출할 캠페인을 고르고 차감액을 정한다. DB 를 쓰지 않는 계산만 담는다.
 *
 * <p>설계 기록 2026-11-12-campaign-spend-and-detail-api 의 규칙을 따른다.
 */
final class BidAuction {

    private BidAuction() {
    }

    /**
     * 입찰가에 비례한 확률로 1개를 고른다. 입찰가 50 과 30 이면 각각 5/8, 3/8 확률이다.
     *
     * @param roll 0 이상 1 미만의 난수
     */
    static Campaign pick(List<Campaign> candidates, double roll) {
        long total = candidates.stream().mapToLong(Campaign::getBidPrice).sum();
        double target = roll * total;
        long cumulative = 0;
        for (Campaign candidate : candidates) {
            cumulative += candidate.getBidPrice();
            if (target < cumulative) {
                return candidate;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    /**
     * 차감액 = min(내 입찰가, 나보다 낮은 후보 입찰가 중 최고 + 1원). 나보다 낮은 후보가 없으면 내 입찰가.
     */
    static long charge(Campaign winner, List<Campaign> candidates) {
        long bid = winner.getBidPrice();
        OptionalLong nextBid = candidates.stream()
                .mapToLong(Campaign::getBidPrice)
                .filter(other -> other < bid)
                .max();
        return nextBid.isPresent() ? Math.min(bid, nextBid.getAsLong() + 1) : bid;
    }
}
