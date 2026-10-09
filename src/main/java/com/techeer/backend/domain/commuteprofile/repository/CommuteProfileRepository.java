package com.techeer.backend.domain.commuteprofile.repository;

import com.techeer.backend.domain.commuteprofile.entity.CommuteProfile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommuteProfileRepository extends JpaRepository<CommuteProfile, Long> {
    List<CommuteProfile> findAllByMemberId(Long memberId);
}
