package com.techeer.backend.domain.advertiser.repository;

import com.techeer.backend.domain.advertiser.entity.Advertiser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdvertiserRepository extends JpaRepository<Advertiser, Long> {

    boolean existsByEmail(String email);

    Optional<Advertiser> findByEmail(String email);
}
