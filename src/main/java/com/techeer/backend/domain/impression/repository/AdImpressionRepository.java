package com.techeer.backend.domain.impression.repository;

import com.techeer.backend.domain.impression.entity.AdImpression;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdImpressionRepository extends JpaRepository<AdImpression, String> {
}
