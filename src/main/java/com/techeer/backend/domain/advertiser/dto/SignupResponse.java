package com.techeer.backend.domain.advertiser.dto;

import com.techeer.backend.domain.advertiser.entity.Advertiser;
import com.techeer.backend.domain.advertiser.entity.Plan;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "광고주 회원가입 응답 (비밀번호·해시는 포함하지 않는다)")
public record SignupResponse(
        @Schema(description = "광고주 ID", example = "1") Long userId,
        @Schema(description = "이메일", example = "owner@techeer.com") String email,
        @Schema(description = "사업자명", example = "테커 카페") String businessName,
        @Schema(description = "요금제", example = "FREE") Plan plan,
        @Schema(description = "가입 시각") LocalDateTime createdAt) {

    /**
     * 저장된 광고주 엔티티에서 응답에 필요한 값만 꺼내 만든다. 엔티티를 그대로 반환하지 않기 위한 변환이다.
     */
    public static SignupResponse from(Advertiser advertiser) {
        return new SignupResponse(
                advertiser.getUserId(),
                advertiser.getEmail(),
                advertiser.getBusinessName(),
                advertiser.getPlan(),
                advertiser.getCreatedAt());
    }
}
