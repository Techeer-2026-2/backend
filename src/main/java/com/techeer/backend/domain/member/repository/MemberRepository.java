package com.techeer.backend.domain.member.repository;

import com.techeer.backend.domain.member.entity.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByEmail(String email);

    /**
     * 탈퇴(soft delete)하지 않은 회원만 이메일로 조회한다. 로그인에 쓴다.
     */
    Optional<Member> findByEmailAndDeletedAtIsNull(String email);
}
