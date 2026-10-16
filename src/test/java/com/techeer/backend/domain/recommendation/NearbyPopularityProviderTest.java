package com.techeer.backend.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.techeer.backend.domain.ranking.dto.RankingDtos.ChartResponse;
import com.techeer.backend.domain.ranking.dto.RankingDtos.Item;
import com.techeer.backend.domain.ranking.dto.RankingDtos.Track;
import com.techeer.backend.domain.ranking.service.RankingQueryService;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class NearbyPopularityProviderTest {

    private final RankingQueryService ranking = mock(RankingQueryService.class);
    private final NearbyPopularityProvider provider = new NearbyPopularityProvider(ranking);

    private static Item item(int rank, long listeners, String trackId) {
        return new Item(rank, listeners, new Track(trackId, "곡", "가수", null, null));
    }

    private static ChartResponse chart(String scope, String status, Item... items) {
        return new ChartResponse("성수동 주변", null, null, !"NEARBY".equals(scope), scope, status, List.of(items));
    }

    @Test
    void 내_셀_차트는_청취자_수에_비례해_최대_가산점까지_준다() {
        NearbyPopularity result = NearbyPopularityProvider.toPopularity(chart("NEARBY", "READY",
                item(1, 40, "itunes-100"), item(2, 20, "itunes-200"), item(3, 10, "itunes-300")));

        assertThat(result.applied()).isTrue();
        assertThat(result.scope()).isEqualTo("NEARBY");
        assertThat(result.locationLabel()).isEqualTo("성수동 주변");
        assertThat(result.boostOf(100L)).isCloseTo(NearbyPopularityProvider.MAX_BOOST, within(1e-9));
        assertThat(result.boostOf(200L)).isCloseTo(NearbyPopularityProvider.MAX_BOOST / 2, within(1e-9));
        assertThat(result.boostOf(300L)).isCloseTo(NearbyPopularityProvider.MAX_BOOST / 4, within(1e-9));
        assertThat(result.boostOf(999L)).isZero();
        assertThat(result.boostOf(null)).isZero();
    }

    @Test
    void 차트_범위가_넓어질수록_가산점이_줄어든다() {
        double nearby = top("NEARBY");
        double parent = top("PARENT");
        double city = top("CITY");
        double global = top("GLOBAL");

        assertThat(nearby).isGreaterThan(parent);
        assertThat(parent).isGreaterThan(city);
        assertThat(city).isGreaterThan(global);
        assertThat(global).isGreaterThan(0.0);
    }

    private double top(String scope) {
        return NearbyPopularityProvider.toPopularity(chart(scope, "READY", item(1, 30, "itunes-1"))).boostOf(1L);
    }

    @Test
    void 숫자만_있는_ID도_같은_곡으로_보고_연결할_수_없는_ID는_건너뛴다() {
        NearbyPopularity result = NearbyPopularityProvider.toPopularity(chart("NEARBY", "READY",
                item(1, 30, "123"), item(2, 30, "ITUNES-456"), item(3, 30, "demo-track-7"),
                item(4, 30, "itunes-abc"), item(5, 30, "itunes--5"), item(6, 30, " 789 ")));

        assertThat(result.boostByItunesId()).containsOnlyKeys(123L, 456L, 789L);
    }

    @Test
    void 집계_전이거나_데이터가_부족하거나_차트가_비어_있으면_영향을_주지_않는다() {
        assertThat(NearbyPopularityProvider.toPopularity(chart("NONE", "PENDING")).applied()).isFalse();
        assertThat(NearbyPopularityProvider.toPopularity(chart("GLOBAL", "INSUFFICIENT_DATA")).applied()).isFalse();
        assertThat(NearbyPopularityProvider.toPopularity(chart("NEARBY", "READY")).applied()).isFalse();
        assertThat(NearbyPopularityProvider.toPopularity(chart("NEARBY", "READY", item(1, 0, "itunes-1"))).applied())
                .isFalse();
    }

    @Test
    void 알_수_없는_범위는_가산점을_주지_않는다() {
        assertThat(NearbyPopularityProvider.toPopularity(chart("REGION", "READY", item(1, 9, "itunes-1"))).applied())
                .isFalse();
    }

    @Test
    void 위치를_모르면_랭킹을_조회하지_않는다() {
        assertThat(provider.find(null, 127.0).applied()).isFalse();
        assertThat(provider.find(37.5, null).applied()).isFalse();
        verifyNoInteractions(ranking);
    }

    @Test
    void 위치로_랭킹_차트_상위_50곡을_조회해_가산점으로_바꾼다() {
        when(ranking.nearby(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(chart("NEARBY", "READY", item(1, 10, "itunes-1")));

        NearbyPopularity result = provider.find(37.55, 127.04);

        assertThat(result.boostOf(1L)).isGreaterThan(0.0);
        verify(ranking).nearby(37.55, 127.04, 50);
    }

    @Test
    void 랭킹_조회가_실패해도_예외를_던지지_않고_영향_없음으로_돌려준다() {
        when(ranking.nearby(anyDouble(), anyDouble(), anyInt()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT));
        assertThat(provider.find(37.5, 127.0).applied()).isFalse();

        doThrow(new IllegalStateException("db down")).when(ranking).nearby(anyDouble(), anyDouble(), anyInt());
        assertThat(provider.find(37.5, 127.0).applied()).isFalse();
    }
}
