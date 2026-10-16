package com.techeer.backend.domain.playlist.entity;

import com.techeer.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 곡 하나가 어떤 분위기에 얼마나 맞는지(0~1). 곡 하나에 여러 분위기가 붙을 수 있다. */
@Getter
@Entity
@Table(name = "track_moods", uniqueConstraints = @UniqueConstraint(columnNames = {"track_id", "mood"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrackMoodScore extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id", nullable = false)
    private Track track;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrackMood mood;

    @Column(nullable = false)
    private double score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrackMoodSource source;

    private TrackMoodScore(Track track, TrackMood mood, double score, TrackMoodSource source) {
        this.track = track;
        this.mood = mood;
        this.score = score;
        this.source = source;
    }

    public static TrackMoodScore create(Track track, TrackMood mood, double score, TrackMoodSource source) {
        return new TrackMoodScore(track, mood, requireValidScore(score), source);
    }

    public void rescore(double score, TrackMoodSource source) {
        this.score = requireValidScore(score);
        this.source = source;
    }

    private static double requireValidScore(double score) {
        if (Double.isNaN(score) || score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("분위기 점수는 0~1 사이여야 합니다: " + score);
        }
        return score;
    }
}
