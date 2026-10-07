package com.techeer.backend.domain.advertiser.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 내 정보 수정 요청. 지금은 사업자명만 바꿀 수 있다. 이메일·요금제는 이 요청으로 바꿀 수 없도록 필드 자체를 두지 않았다.
 */
@Schema(description = "광고주 내 정보 수정 요청")
public record UpdateAdvertiserRequest(
        @Schema(description = "새 사업자명", example = "테커 로스터리")
        @NotBlank
        @Size(max = 128)
        String businessName) {
}
