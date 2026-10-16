package com.techeer.backend.domain.playlist.entity;

import com.techeer.backend.global.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "playlist_tracks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaylistTrack extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playlist_id", nullable = false)
    private Playlist playlist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id", nullable = false)
    private Track track;

    private Integer trackOrder;

    @Builder
    private PlaylistTrack(Playlist playlist, Track track) {
        this.playlist = playlist;
        this.track = track;
    }

    public void updateOrder(int trackOrder) {
        this.trackOrder = trackOrder;
    }
}
