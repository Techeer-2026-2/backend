package com.techeer.backend.domain.campaign.service;

import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.util.List;
import java.util.Set;

/**
 * 캠페인 타겟 조건(연령대·성별)의 허용 값과 검사 규칙.
 */
public final class CampaignTargetRules {

    /** 연령대 코드. 10s ~ 50s */
    public static final Set<String> AGE_GROUPS = Set.of("10s", "20s", "30s", "40s", "50s");

    /** 성별 코드. all 은 성별 제한 없음을 뜻한다. */
    public static final Set<String> GENDERS = Set.of("male", "female", "all");

    private CampaignTargetRules() {
    }

    /**
     * 연령대 목록과 성별 목록을 검사한다. 비어 있거나, 허용되지 않은 값이 있거나,
     * all 과 다른 성별을 같이 고른 경우 INVALID_INPUT 으로 막는다.
     *
     * @throws BusinessException 규칙을 어긴 경우
     */
    public static void validate(List<String> ageGroups, List<String> genders) {
        validateAgeGroups(ageGroups);
        validateGenders(genders);
    }

    /**
     * 연령대 목록만 검사한다. 수정 요청에서 연령대를 보낸 경우에 쓴다.
     */
    public static void validateAgeGroups(List<String> ageGroups) {
        if (ageGroups == null || ageGroups.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        for (String ageGroup : ageGroups) {
            if (ageGroup == null || !AGE_GROUPS.contains(ageGroup)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
        }
    }

    /**
     * 성별 목록만 검사한다. 수정 요청에서 성별을 보낸 경우에 쓴다.
     */
    public static void validateGenders(List<String> genders) {
        if (genders == null || genders.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        for (String gender : genders) {
            if (gender == null || !GENDERS.contains(gender)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
        }
        if (genders.contains("all") && genders.size() > 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
