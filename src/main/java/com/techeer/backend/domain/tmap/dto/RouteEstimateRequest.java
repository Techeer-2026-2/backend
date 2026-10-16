package com.techeer.backend.domain.tmap.dto;

import com.techeer.backend.domain.tmap.TransportMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record RouteEstimateRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double startLatitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double startLongitude,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double endLatitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double endLongitude,
        @NotNull TransportMode transportMode) {
}
