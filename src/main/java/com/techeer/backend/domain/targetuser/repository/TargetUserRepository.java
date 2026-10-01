package com.techeer.backend.domain.targetuser.repository;

import com.techeer.backend.domain.targetuser.entity.TargetUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TargetUserRepository extends JpaRepository<TargetUser, Long> {
}
