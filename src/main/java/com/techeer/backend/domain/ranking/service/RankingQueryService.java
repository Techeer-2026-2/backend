package com.techeer.backend.domain.ranking.service;

import com.techeer.backend.domain.ranking.dto.RankingDtos.ChartResponse;
import com.techeer.backend.domain.ranking.entity.RankingModels.Batch;
import com.techeer.backend.domain.ranking.repository.RankingRepository;
import com.techeer.backend.global.config.ClockConfig;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RankingQueryService {
    private final RankingRepository repository;
    private final RankingGeo geo;

    // 배치 교체 도중에도 hour, scope, items가 같은 DB snapshot을 읽는다.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ChartResponse nearby(double lat, double lng, int limit) {
        String cell = geo.cell(lat, lng);
        List<String> keys = new ArrayList<>();
        keys.add("NEARBY:" + cell);
        keys.add("PARENT:" + geo.parent(cell));
        repository.city(cell).ifPresent(city -> keys.add("CITY:" + city.id()));
        keys.add("GLOBAL");
        return query(keys, limit, true);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ChartResponse global(int limit) {
        return query(List.of("GLOBAL"), limit, false);
    }

    private ChartResponse query(List<String> keys, int limit, boolean nearby) {
        if (limit < 1 || limit > RankingCalculator.MAX_ITEMS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Batch batch = repository.latestBatch().orElse(null);
        if (batch == null) {
            return new ChartResponse("집계 준비 중", null, null, false, "NONE", "PENDING", List.of());
        }
        for (String key : keys) {
            var label = repository.eligibleLabel(batch.hour(), key);
            if (label.isPresent()) {
                String scope = key.split(":", 2)[0];
                return response(batch, label.get(), nearby && !scope.equals("NEARBY"), scope, "READY",
                        repository.items(batch.hour(), key, limit));
            }
        }
        return response(batch, "전체 인기곡", nearby, "GLOBAL", "INSUFFICIENT_DATA", List.of());
    }

    private ChartResponse response(Batch batch, String label, boolean fallback, String scope,
                                   String status, List<com.techeer.backend.domain.ranking.dto.RankingDtos.Item> items) {
        return new ChartResponse(label, batch.hour().atZone(ClockConfig.SEOUL).toOffsetDateTime(),
                batch.updatedAt().atZone(ClockConfig.SEOUL).toOffsetDateTime(), fallback, scope, status, items);
    }
}
