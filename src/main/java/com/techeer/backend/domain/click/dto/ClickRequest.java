package com.techeer.backend.domain.click.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "클릭 기록 요청")
public record ClickRequest(
        @Schema(description = "클릭한 배너 노출의 알림 ID", example = "3f2b8c1e-9a4d-4e7b-8c21-5d6f7a8b9c0d")
        @NotBlank
        @Size(max = 36)
        String notificationId) {
}
