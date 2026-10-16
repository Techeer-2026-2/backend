package com.techeer.backend.domain.commutesession.repository;

import com.techeer.backend.domain.commutesession.entity.CommuteSession;
import com.techeer.backend.domain.commutesession.entity.CommuteSessionStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommuteSessionRepository extends JpaRepository<CommuteSession, Long> {

    Optional<CommuteSession> findByIdAndMemberId(Long id, Long memberId);

    Optional<CommuteSession> findByMemberIdAndStatus(Long memberId, CommuteSessionStatus status);

    /**
     * 진행 중인 통근을 끝낸다(종료·취소). 바뀐 행 수(0 또는 1)를 돌려준다. 이미 끝났거나 내 통근이 아니면 0 이다.
     *
     * <p>"읽어서 진행 중인지 보고 고치는" 두 단계 대신 조건을 걸어 한 번에 바꾸므로, 종료와 취소 요청이 동시에 와도 한쪽만 성공한다.
     * {@code activeMemberId} 를 비워서 같은 회원이 다음 통근을 시작할 수 있게 한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CommuteSession s
            set s.status = :status, s.endedAt = :now, s.updatedAt = :now, s.activeMemberId = null
            where s.id = :id and s.memberId = :memberId and s.status = com.techeer.backend.domain.commutesession.entity.CommuteSessionStatus.ACTIVE
            """)
    int finish(@Param("id") Long id, @Param("memberId") Long memberId,
            @Param("status") CommuteSessionStatus status, @Param("now") LocalDateTime now);
}
