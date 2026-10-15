package com.techeer.backend.domain.tmap.dto;

public record TmapPedestrianRouteRequest(
        double startX, double startY, double endX, double endY, String startName, String endName) {

    public static TmapPedestrianRouteRequest of(double startX, double startY, double endX, double endY) {
        return new TmapPedestrianRouteRequest(startX, startY, endX, endY, "출발", "도착");
    }
}
