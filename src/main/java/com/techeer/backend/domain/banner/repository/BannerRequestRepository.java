package com.techeer.backend.domain.banner.repository;

import com.techeer.backend.domain.banner.entity.BannerRequest;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BannerRequestRepository extends JpaRepository<BannerRequest, String> {

    /**
     * 요청 ID 를 넣는다. 이미 있으면 아무것도 하지 않는다(ON CONFLICT DO NOTHING).
     * 반환값 1: 새로 들어감 (처음 요청 → 차감 진행). 0: 이미 있음 (재시도 → 저장된 결과를 돌려준다).
     * JPA save 는 같은 ID 를 갱신(merge)하므로 중복 감지에 쓰지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO banner_requests (request_id, target_user_id, notification_id, created_at) "
            + "VALUES (:requestId, :targetUserId, NULL, :now) ON CONFLICT (request_id) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("requestId") String requestId,
                       @Param("targetUserId") Long targetUserId,
                       @Param("now") LocalDateTime now);
}
