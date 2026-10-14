package com.techeer.backend.domain.commuteprofile.repository;

import com.techeer.backend.domain.commuteprofile.entity.CommuteRoute;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommuteRouteRepository extends JpaRepository<CommuteRoute, Long> {

    Optional<CommuteRoute> findByCommuteProfileId(Long commuteProfileId);
}
