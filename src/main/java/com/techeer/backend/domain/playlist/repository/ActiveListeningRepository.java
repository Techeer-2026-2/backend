package com.techeer.backend.domain.playlist.repository;

import com.techeer.backend.domain.playlist.entity.ActiveListening;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActiveListeningRepository extends JpaRepository<ActiveListening, Long> {

    /**
     * 회원의 현재 재생 상태를 저장한다. 행이 없으면 넣고, 있으면 덮어쓴다. 바뀐 행 수(0 또는 1)를 돌려준다.
     *
     * <p>"읽어서 있으면 고치고 없으면 넣는" 두 단계 대신 DB 가 한 번에 처리하므로, 같은 회원의 첫 신호가 동시에 와도 실패하지 않는다.
     * 이미 저장된 위치 측정 시각보다 오래된 신호는 덮어쓰지 않고 0 을 돌려준다(늦게 도착한 요청이 최신 셀을 되돌리지 않게 한다).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into playlist_active_listenings
                (member_id, playlist_id, h3_cell, location_recorded_at, heartbeat_at)
            values (:memberId, :playlistId, :h3Cell, :recordedAt, :now)
            on conflict (member_id) do update set
                playlist_id = excluded.playlist_id,
                h3_cell = excluded.h3_cell,
                location_recorded_at = excluded.location_recorded_at,
                heartbeat_at = excluded.heartbeat_at
            where playlist_active_listenings.location_recorded_at <= excluded.location_recorded_at
            """, nativeQuery = true)
    int upsert(@Param("memberId") Long memberId, @Param("playlistId") Long playlistId,
            @Param("h3Cell") String h3Cell, @Param("recordedAt") Instant recordedAt, @Param("now") Instant now);

    /** 마지막 신호와 위치가 모두 기준 시각 이후인, 같은 셀에서 같은 플레이리스트를 듣는 회원 수. */
    @Query("""
            select count(a) from ActiveListening a
            where a.playlistId = :playlistId and a.h3Cell = :h3Cell
              and a.heartbeatAt >= :heartbeatCutoff and a.locationRecordedAt >= :locationCutoff
            """)
    long countActive(@Param("playlistId") Long playlistId, @Param("h3Cell") String h3Cell,
            @Param("heartbeatCutoff") Instant heartbeatCutoff, @Param("locationCutoff") Instant locationCutoff);

    /** 회원이 해당 플레이리스트를 듣는 중일 때만 지운다. 다른 플레이리스트로 옮긴 뒤 도착한 종료 요청이 새 재생을 끊지 않게 한다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ActiveListening a where a.memberId = :memberId and a.playlistId = :playlistId")
    int deleteByMemberAndPlaylist(@Param("memberId") Long memberId, @Param("playlistId") Long playlistId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ActiveListening a where a.playlistId = :playlistId")
    int deleteForPlaylist(@Param("playlistId") Long playlistId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from ActiveListening a
            where a.heartbeatAt < :heartbeatCutoff or a.locationRecordedAt < :locationCutoff
            """)
    int deleteExpired(@Param("heartbeatCutoff") Instant heartbeatCutoff,
            @Param("locationCutoff") Instant locationCutoff);
}
