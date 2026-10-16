package com.techeer.backend.domain.user.service;

import com.techeer.backend.domain.user.dto.UpdateUserRequest;
import com.techeer.backend.domain.user.dto.UserResponse;
import com.techeer.backend.domain.user.entity.User;
import com.techeer.backend.domain.user.repository.UserRepository;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인한 음악 앱 회원 본인의 정보를 조회·수정한다.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * @throws BusinessException 없거나 탈퇴한 회원인 경우
     */
    @Transactional(readOnly = true)
    public UserResponse getMe(Long userId) {
        return UserResponse.from(findActive(userId));
    }

    /**
     * 닉네임을 수정한다. 영속 상태의 엔티티 값을 바꾸면 트랜잭션이 끝날 때 JPA 가 UPDATE 를 실행한다(더티 체킹).
     *
     * @throws BusinessException 없거나 탈퇴한 회원인 경우
     */
    @Transactional
    public UserResponse updateMe(Long userId, UpdateUserRequest request) {
        User user = findActive(userId);
        user.changeNickname(request.nickname().trim());
        return UserResponse.from(user);
    }

    private User findActive(Long userId) {
        return userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
