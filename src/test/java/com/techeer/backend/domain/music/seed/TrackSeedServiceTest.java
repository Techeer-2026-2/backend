package com.techeer.backend.domain.music.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.techeer.backend.domain.music.dto.TrackCandidateResponse;
import com.techeer.backend.domain.music.service.MusicSearchService;
import com.techeer.backend.domain.playlist.entity.TrackMood;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TrackSeedServiceTest {

    private final MusicSearchService search = mock(MusicSearchService.class);
    private final TrackSeedWriter writer = mock(TrackSeedWriter.class);
    // 간격·재시도 대기는 0 으로 두어 테스트가 기다리지 않게 한다.
    private final TrackSeedService service = new TrackSeedService(search, writer, 0, 0, 2, 3);

    private static TrackCandidateResponse track(long id, String genre, String preview, Integer millis) {
        return new TrackCandidateResponse(id, "곡" + id, "가수", "앨범", "art", preview, millis, genre);
    }

    private int totalQueries() {
        return TrackSeedCatalog.QUERIES.values().stream().mapToInt(List::size).sum();
    }

    @Test
    void 제외_장르와_미리듣기_없는_곡은_저장하지_않는다() {
        when(search.search(anyString())).thenReturn(List.of(
                track(1, "Pop", "p", 1000),
                track(2, "Classical", "p", 1000),
                track(3, "Pop", null, 1000),
                track(4, "Pop", "p", null)));
        when(writer.save(any(), any())).thenReturn(new TrackSeedWriter.SaveResult(1, 0, 0));

        TrackSeedService.Summary summary = service.run();

        ArgumentCaptor<List<TrackCandidateResponse>> saved = ArgumentCaptor.captor();
        verify(writer, times(totalQueries())).save(any(), saved.capture());
        // 어떤 검색어에서도 2(제외 장르)·3(미리듣기 없음)·4(길이 없음)는 저장 대상에 없다. 1 은 허용 장르가 없는 검색어에서만 남는다.
        assertThat(saved.getAllValues()).allSatisfy(list ->
                assertThat(list).extracting(TrackCandidateResponse::itunesTrackId).isSubsetOf(1L));
        assertThat(saved.getAllValues()).anySatisfy(list -> assertThat(list).hasSize(1));
        assertThat(summary.aborted()).isFalse();
        assertThat(summary.queries()).isEqualTo(totalQueries());
    }

    @Test
    void 검색어에_허용_장르가_있으면_그_장르만_저장한다() {
        when(search.search(anyString())).thenReturn(List.of(track(1, "Pop", "p", 1000), track(2, "K-Pop", "p", 1000)));
        when(writer.save(any(), any())).thenReturn(new TrackSeedWriter.SaveResult(0, 0, 0));

        service.run();

        ArgumentCaptor<List<TrackCandidateResponse>> saved = ArgumentCaptor.captor();
        verify(writer, times(totalQueries())).save(any(), saved.capture());
        // 서비스는 분위기 순서대로, 각 분위기 안에서는 목록 순서대로 호출한다. "k-pop hits"(K-Pop 만 허용)가 몇 번째인지 구한다.
        int index = 0;
        int kpopIndex = -1;
        for (TrackMood mood : TrackMood.values()) {
            for (TrackSeedCatalog.Query query : TrackSeedCatalog.QUERIES.getOrDefault(mood, List.of())) {
                if (query.term().equals("k-pop hits")) {
                    kpopIndex = index;
                }
                index++;
            }
        }
        assertThat(kpopIndex).isGreaterThanOrEqualTo(0);
        assertThat(saved.getAllValues().get(kpopIndex))
                .extracting(TrackCandidateResponse::itunesTrackId).containsExactly(2L);
        // 허용 장르가 없는 검색어에서는 두 곡 모두 저장된다.
        assertThat(saved.getAllValues().get(0))
                .extracting(TrackCandidateResponse::itunesTrackId).containsExactly(1L, 2L);
    }

    @Test
    void 한_번_실패해도_재시도해서_성공하면_이어간다() {
        when(search.search("sad song"))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_ERROR))
                .thenReturn(List.of(track(1, "Pop", "p", 1000)));
        when(search.search(org.mockito.ArgumentMatchers.argThat(t -> !"sad song".equals(t)))).thenReturn(List.of());
        when(writer.save(any(), any())).thenReturn(new TrackSeedWriter.SaveResult(1, 0, 0));

        TrackSeedService.Summary summary = service.run();

        verify(search, times(2)).search("sad song");
        assertThat(summary.failedQueries()).isZero();
        assertThat(summary.aborted()).isFalse();
    }

    @Test
    void 연속으로_실패하면_막힌_것으로_보고_중단한다() {
        when(search.search(anyString())).thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_ERROR));

        TrackSeedService.Summary summary = service.run();

        assertThat(summary.aborted()).isTrue();
        assertThat(summary.failedQueries()).isEqualTo(3);
        // 검색어 3개 × (처음 1회 + 재시도 2회) 만 부르고 멈춘다. 전체를 다 돌지 않는다.
        verify(search, times(9)).search(anyString());
        verify(writer, times(0)).save(any(), any());
    }
}
