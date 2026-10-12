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

    private Long itunesTrackId;

    private String trackName;

    private String artistName;

    private String collectionName;

    private String artworkUrl;

    private String previewUrl;

    private Integer trackTimeMillis;

    @Builder
    private PlaylistTrack(Playlist playlist, Long itunesTrackId, String trackName, String artistName,
            String collectionName, String artworkUrl, String previewUrl, Integer trackTimeMillis) {
        this.playlist = playlist;
        this.itunesTrackId = itunesTrackId;
        this.trackName = trackName;
        this.artistName = artistName;
        this.collectionName = collectionName;
        this.artworkUrl = artworkUrl;
        this.previewUrl = previewUrl;
        this.trackTimeMillis = trackTimeMillis;
    }
}
