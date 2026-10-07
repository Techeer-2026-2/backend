package com.techeer.backend.domain.advertiser.repository;

import com.techeer.backend.domain.advertiser.entity.Advertiser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdvertiserRepository extends JpaRepository<Advertiser, Long> {

    boolean existsByEmail(String email);

    Optional<Advertiser> findByEmail(String email);

    /**
     * 탈퇴(soft delete)하지 않은 광고주만 이메일로 조회한다. 로그인에 쓴다.
     */
    Optional<Advertiser> findByEmailAndDeletedAtIsNull(String email);
}
