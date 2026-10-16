package com.techeer.backend.domain.playlist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 지금 듣고 있는 공유 플레이리스트와 그 위치(H3 셀). 회원당 한 행만 유지해서 같은 회원의 중복 신호가 한 명으로 센다.
 *
 * <p>행은 재생 신호(heartbeat)를 받을 때마다 갱신하고, 신호가 끊기면 {@code ListeningCleanupJob} 이 지운다.
 * 앱이 보낸 값을 신뢰하는 수치라서 개인을 식별하는 정보는 저장하지 않고 회원 ID 와 셀만 둔다.
 */
@Getter
@Entity
@Table(name = "playlist_active_listenings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActiveListening {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "playlist_id", nullable = false)
    private Long playlistId;

    @Column(name = "h3_cell", nullable = false, length = 15)
    private String h3Cell;

    @Column(name = "location_recorded_at", nullable = false)
    private Instant locationRecordedAt;

    @Column(name = "heartbeat_at", nullable = false)
    private Instant heartbeatAt;

    public ActiveListening(
            Long memberId, Long playlistId, String h3Cell, Instant locationRecordedAt, Instant heartbeatAt) {
        this.memberId = memberId;
        this.playlistId = playlistId;
        this.h3Cell = h3Cell;
        this.locationRecordedAt = locationRecordedAt;
        this.heartbeatAt = heartbeatAt;
    }
}
