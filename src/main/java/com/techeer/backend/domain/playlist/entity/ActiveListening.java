package com.techeer.backend.domain.playlist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "music_active_listening", schema = "public")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActiveListening {
    @Id
    private Long userId;

    @Column(nullable = false)
    private Long playlistId;

    @Column(nullable = false, length = 15)
    private String h3Cell;

    @Column(nullable = false)
    private Instant locationRecordedAt;

    @Column(nullable = false)
    private Instant heartbeatAt;

    public ActiveListening(Long userId, Long playlistId, String h3Cell,
                           Instant locationRecordedAt, Instant heartbeatAt) {
        this.userId = userId;
        update(playlistId, h3Cell, locationRecordedAt, heartbeatAt);
    }

    public void update(Long playlistId, String h3Cell, Instant locationRecordedAt, Instant heartbeatAt) {
        this.playlistId = playlistId;
        this.h3Cell = h3Cell;
        this.locationRecordedAt = locationRecordedAt;
        this.heartbeatAt = heartbeatAt;
    }
}
