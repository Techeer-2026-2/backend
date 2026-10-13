package com.techeer.backend.domain.playlist.entity;

import com.techeer.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "tracks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Track extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long itunesTrackId;

    private String title;

    private String artist;

    private String genre;

    private String collectionName;

    private String artworkUrl;

    private String previewUrl;

    private Integer trackTimeMillis;

    @Builder
    private Track(Long itunesTrackId, String title, String artist, String genre, String collectionName,
            String artworkUrl, String previewUrl, Integer trackTimeMillis) {
        this.itunesTrackId = itunesTrackId;
        this.title = title;
        this.artist = artist;
        this.genre = genre;
        this.collectionName = collectionName;
        this.artworkUrl = artworkUrl;
        this.previewUrl = previewUrl;
        this.trackTimeMillis = trackTimeMillis;
    }
}
