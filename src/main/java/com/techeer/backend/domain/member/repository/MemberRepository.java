package com.techeer.backend.domain.member.repository;

import com.techeer.backend.domain.member.entity.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByEmail(String email);

    /**
     * 탈퇴(soft delete)하지 않은 회원만 이메일로 조회한다. 로그인에 쓴다.
     */
    Optional<Member> findByEmailAndDeletedAtIsNull(String email);

    /**
     * 저장된 refresh token 해시가 oldHash 와 같을 때만 newHash 로 바꾼다. 바뀐 행 수(0 또는 1)를 돌려준다.
     *
     * <p>"읽어서 비교하고 쓰는" 두 단계 대신 DB 에서 비교와 교체를 한 번에 하므로, 같은 refresh token 으로
     * 동시에 요청이 여러 번 와도 한 번만 성공한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Member m set m.refreshTokenHash = :newHash
            where m.memberId = :memberId and m.refreshTokenHash = :oldHash and m.deletedAt is null
            """)
    int rotateRefreshTokenHash(
            @Param("memberId") Long memberId,
            @Param("oldHash") String oldHash,
            @Param("newHash") String newHash);

    /**
     * 저장된 refresh token 해시가 hash 와 같을 때만 지운다(로그아웃). 지운 행 수(0 또는 1)를 돌려준다.
     *
     * <p>조건에 해시를 넣은 이유는 이미 교체된 옛 토큰으로 로그아웃을 요청해도 지금 쓰고 있는 새 세션이 끊기지 않게 하기 위해서다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Member m set m.refreshTokenHash = null
            where m.memberId = :memberId and m.refreshTokenHash = :hash
            """)
    int clearRefreshTokenHash(@Param("memberId") Long memberId, @Param("hash") String hash);
}
