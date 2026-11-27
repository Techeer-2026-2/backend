package com.techeer.backend.domain.click.repository;

import com.techeer.backend.domain.click.entity.ClickFallback;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClickFallbackRepository extends JpaRepository<ClickFallback, Long> {

    List<ClickFallback> findTop500ByStatusOrderByFallbackIdAsc(ClickFallback.Status status);
}
