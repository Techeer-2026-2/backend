package com.techeer.backend.domain.commuteprofile.entity;

import com.techeer.backend.domain.tmap.TransportMode;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "commute_routes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommuteRoute extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commute_profile_id", nullable = false)
    private CommuteProfile commuteProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransportMode transportMode;

    @Column(nullable = false)
    private Integer averageDurationMinutes;

    @Column(columnDefinition = "jsonb")
    private String routeDetail;

    @Builder
    private CommuteRoute(CommuteProfile commuteProfile, TransportMode transportMode,
            Integer averageDurationMinutes, String routeDetail) {
        this.commuteProfile = commuteProfile;
        this.transportMode = transportMode;
        this.averageDurationMinutes = averageDurationMinutes;
        this.routeDetail = routeDetail;
    }

    public void update(TransportMode transportMode, Integer averageDurationMinutes, String routeDetail) {
        this.transportMode = transportMode;
        this.averageDurationMinutes = averageDurationMinutes;
        this.routeDetail = routeDetail;
    }
}
