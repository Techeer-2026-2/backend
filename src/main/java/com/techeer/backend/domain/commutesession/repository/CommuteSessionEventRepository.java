package com.techeer.backend.domain.commutesession.repository;

import com.techeer.backend.domain.commutesession.entity.CommuteSessionEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommuteSessionEventRepository extends JpaRepository<CommuteSessionEvent, Long> {

    Optional<CommuteSessionEvent> findByEventId(UUID eventId);

    /**
     * 같은 eventId 가 없을 때만 넣는다. 넣었으면 1, 이미 있어서 건너뛰었으면 0 을 돌려준다.
     *
     * <p>"있는지 읽고 없으면 넣는" 두 단계 대신 DB 가 한 번에 처리하므로, 같은 이벤트가 동시에 여러 번 와도 한 번만 들어간다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into commute_session_events
                (created_at, updated_at, event_id, session_id, itunes_track_id, action_type, played_seconds)
            values (:now, :now, :eventId, :sessionId, :itunesTrackId, :action, :playedSeconds)
            on conflict (event_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") UUID eventId, @Param("sessionId") Long sessionId,
            @Param("itunesTrackId") Long itunesTrackId, @Param("action") String action,
            @Param("playedSeconds") Integer playedSeconds, @Param("now") LocalDateTime now);

    /** 통근 하나의 이벤트를 곡·행동별로 묶은 개수와 들은 시간 합계. */
    @Query("""
            select new com.techeer.backend.domain.commutesession.repository.EventTotals(
                e.itunesTrackId, e.action, count(e), coalesce(sum(e.playedSeconds), 0L))
            from CommuteSessionEvent e
            where e.sessionId = :sessionId
            group by e.itunesTrackId, e.action
            """)
    List<EventTotals> totalsBySession(@Param("sessionId") Long sessionId);
}
