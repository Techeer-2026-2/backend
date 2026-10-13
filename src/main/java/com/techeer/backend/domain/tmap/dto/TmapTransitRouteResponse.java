package com.techeer.backend.domain.tmap.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TmapTransitRouteResponse(MetaData metaData) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MetaData(Plan plan) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Plan(List<Itinerary> itineraries) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Itinerary(Integer totalTime) {
    }
}
