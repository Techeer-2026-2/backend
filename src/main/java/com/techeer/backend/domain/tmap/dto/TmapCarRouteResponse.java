package com.techeer.backend.domain.tmap.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TmapCarRouteResponse(List<Feature> features) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Feature(Properties properties) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Properties(Integer totalTime) {
    }
}
