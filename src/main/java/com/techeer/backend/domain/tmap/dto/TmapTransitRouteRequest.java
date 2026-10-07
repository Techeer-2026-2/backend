package com.techeer.backend.domain.tmap.dto;

public record TmapTransitRouteRequest(
        double startX, double startY, double endX, double endY, int count, int lang, String format) {

    public static TmapTransitRouteRequest of(double startX, double startY, double endX, double endY) {
        return new TmapTransitRouteRequest(startX, startY, endX, endY, 1, 0, "json");
    }
}
