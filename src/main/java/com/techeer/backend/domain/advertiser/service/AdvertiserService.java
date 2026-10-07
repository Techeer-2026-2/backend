package com.techeer.backend.domain.advertiser.service;

import com.techeer.backend.domain.advertiser.dto.AdvertiserResponse;
import com.techeer.backend.domain.advertiser.dto.SignupRequest;
import com.techeer.backend.domain.advertiser.dto.SignupResponse;
import com.techeer.backend.domain.advertiser.dto.UpdateAdvertiserRequest;
import com.techeer.backend.domain.advertiser.entity.Advertiser;
import com.techeer.backend.domain.advertiser.repository.AdvertiserRepository;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdvertiserService {

    private final AdvertiserRepository advertiserRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 광고주를 가입시킨다. 비밀번호는 bcrypt 로 해시해 저장하고 평문은 남기지 않는다.
     *
     * <p>이메일은 소문자로 통일해 저장한다(A@x.com 과 a@x.com 이 따로 가입되는 것을 막는다).
     * 중복은 두 단계로 막는다. 먼저 existsByEmail 로 빠르게 거르고, 동시에 같은 이메일이 들어와 이 검사를 둘 다 통과하는
     * 경우에는 DB 의 unique 제약이 마지막으로 막으며 그 위반도 같은 409 로 바꿔 돌려준다.
     *
     * @param request 가입 요청
     * @return 가입된 광고주 정보 (비밀번호 제외)
     * @throws BusinessException 이미 가입된 이메일인 경우
     */
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);

        if (advertiserRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }

        Advertiser advertiser = Advertiser.create(
                email, passwordEncoder.encode(request.password()), request.businessName().trim());

        try {
            // save 가 아니라 saveAndFlush: 제약 위반이 이 try 안에서 바로 드러나야 잡을 수 있다.
            advertiserRepository.saveAndFlush(advertiser);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        return SignupResponse.from(advertiser);
    }

    /**
     * 광고주 본인 정보를 조회한다. 읽기만 하므로 readOnly 트랜잭션으로 연다.
     *
     * @param advertiserId 광고주 ID
     * @return 광고주 정보 (비밀번호 제외)
     * @throws BusinessException 없거나 탈퇴한 광고주인 경우
     */
    @Transactional(readOnly = true)
    public AdvertiserResponse getMe(Long advertiserId) {
        return AdvertiserResponse.from(findActive(advertiserId));
    }

    /**
     * 사업자명을 수정한다. 영속 상태의 엔티티 값을 바꾸면 트랜잭션이 끝날 때 JPA 가 UPDATE 를 실행한다(더티 체킹).
     *
     * @param advertiserId 광고주 ID
     * @param request 새 사업자명
     * @return 수정된 광고주 정보
     * @throws BusinessException 없거나 탈퇴한 광고주인 경우
     */
    @Transactional
    public AdvertiserResponse updateMe(Long advertiserId, UpdateAdvertiserRequest request) {
        Advertiser advertiser = findActive(advertiserId);
        advertiser.changeBusinessName(request.businessName().trim());
        return AdvertiserResponse.from(advertiser);
    }

    private Advertiser findActive(Long advertiserId) {
        return advertiserRepository.findByUserIdAndDeletedAtIsNull(advertiserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADVERTISER_NOT_FOUND));
    }
}
