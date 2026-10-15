package com.techeer.backend.domain.ranking.service;

import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import com.uber.h3core.H3Core;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RankingGeo {
    private final H3Core h3;

    public RankingGeo() throws IOException {
        h3 = H3Core.newInstance();
    }

    public String cell(double lat, double lng) {
        if (!Double.isFinite(lat) || !Double.isFinite(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return h3.latLngToCellAddress(lat, lng, 8);
    }

    public List<String> neighbors(String cell) {
        return h3.gridDisk(cell, 1);
    }

    public String parent(String cell) {
        return h3.cellToParentAddress(cell, 7);
    }
}
