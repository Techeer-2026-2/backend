package com.techeer.backend.domain.playlist.entity;

import com.techeer.backend.global.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "music_playlists", schema = "public")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MusicPlaylist extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long playlistId;

    @Column(nullable = false)
    private Long ownerUserId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(unique = true, length = 36)
    private String shareCode;

    @ElementCollection
    @CollectionTable(name = "music_playlist_tracks", schema = "public",
            joinColumns = @JoinColumn(name = "playlist_id"))
    @OrderColumn(name = "position")
    @Column(name = "track_id", nullable = false, length = 128)
    private List<String> trackIds = new ArrayList<>();

    public MusicPlaylist(Long ownerUserId, String title, String description, List<String> trackIds) {
        this.ownerUserId = ownerUserId;
        this.title = title;
        this.description = description;
        this.trackIds.addAll(trackIds);
    }

    public void replaceTracks(List<String> newTrackIds) {
        trackIds.clear();
        trackIds.addAll(newTrackIds);
    }

    public String share() {
        if (shareCode == null) {
            shareCode = UUID.randomUUID().toString();
        }
        return shareCode;
    }

    public void revokeShare() {
        shareCode = null;
    }
}
