package com.techeer.backend.domain.account.repository;

import com.techeer.backend.domain.account.entity.MusicUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MusicUserRepository extends JpaRepository<MusicUser, Long> {
    boolean existsByEmail(String email);

    Optional<MusicUser> findByEmailAndDeletedAtIsNull(String email);

    Optional<MusicUser> findByUserIdAndDeletedAtIsNull(Long userId);
}
