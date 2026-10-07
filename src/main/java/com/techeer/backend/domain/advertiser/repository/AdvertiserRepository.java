package com.techeer.backend.domain.advertiser.repository;

import com.techeer.backend.domain.advertiser.entity.Advertiser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdvertiserRepository extends JpaRepository<Advertiser, Long> {

    boolean existsByEmail(String email);

    Optional<Advertiser> findByEmail(String email);

    /**
     * 탈퇴(soft delete)하지 않은 광고주만 id 로 조회한다.
     */
    Optional<Advertiser> findByUserIdAndDeletedAtIsNull(Long userId);

    /**
     * 탈퇴(soft delete)하지 않은 광고주만 이메일로 조회한다. 로그인에 쓴다.
     */
    Optional<Advertiser> findByEmailAndDeletedAtIsNull(String email);

    /**
     * 저장된 refresh token 해시가 oldHash 와 같을 때만 newHash 로 바꾼다. 바뀐 행 수(0 또는 1)를 돌려준다.
     *
     * <p>"읽어서 비교하고 쓰는" 두 단계 대신 DB 에서 비교와 교체를 한 번에 하므로, 같은 refresh token 으로
     * 동시에 요청이 여러 번 와도 한 번만 성공한다. 이 방식은 updated_at 자동 기록을 거치지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Advertiser a set a.refreshTokenHash = :newHash
            where a.userId = :userId and a.refreshTokenHash = :oldHash and a.deletedAt is null
            """)
    int rotateRefreshTokenHash(
            @Param("userId") Long userId,
            @Param("oldHash") String oldHash,
            @Param("newHash") String newHash);
}
