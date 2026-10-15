package com.techeer.backend.domain.user.repository;

import com.techeer.backend.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    /**
     * 탈퇴(soft delete)하지 않은 회원만 이메일로 조회한다. 로그인에 쓴다.
     */
    Optional<User> findByEmailAndDeletedAtIsNull(String email);
}
