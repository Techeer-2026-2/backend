package com.techeer.backend.domain.owner.repository;

import com.techeer.backend.domain.owner.entity.Owner;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerRepository extends JpaRepository<Owner, Long> {

    boolean existsByEmail(String email);

    Optional<Owner> findByEmail(String email);
}
