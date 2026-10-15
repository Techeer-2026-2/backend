package com.techeer.backend.domain.playlist.repository;

import com.techeer.backend.domain.playlist.entity.ActiveListening;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActiveListeningRepository extends JpaRepository<ActiveListening, Long> {
    @Query("""
            select count(a) from ActiveListening a
            where a.playlistId = :playlistId and a.h3Cell = :h3Cell
              and a.heartbeatAt >= :heartbeatCutoff and a.locationRecordedAt >= :locationCutoff
            """)
    long countActive(@Param("playlistId") Long playlistId, @Param("h3Cell") String h3Cell,
                     @Param("heartbeatCutoff") Instant heartbeatCutoff,
                     @Param("locationCutoff") Instant locationCutoff);

    @Modifying
    @Query("delete from ActiveListening a where a.playlistId = :playlistId")
    void deleteForPlaylist(@Param("playlistId") Long playlistId);

    @Modifying
    @Query("""
            delete from ActiveListening a
            where a.heartbeatAt < :heartbeatCutoff or a.locationRecordedAt < :locationCutoff
            """)
    int deleteExpired(@Param("heartbeatCutoff") Instant heartbeatCutoff,
                      @Param("locationCutoff") Instant locationCutoff);
}
