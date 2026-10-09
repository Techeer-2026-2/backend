package com.techeer.backend.domain.commuteprofile.entity;

import com.techeer.backend.domain.tmap.TransportMode;
import com.techeer.backend.global.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
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
@Table(name = "commute_profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommuteProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CommuteType commuteType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransportMode transportMode;

    @Column(nullable = false, length = 100)
    private String frequentRouteName;

    @Column(nullable = false)
    private Integer averageDurationMinutes;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "latitude", column = @Column(name = "departure_latitude", nullable = false)),
        @AttributeOverride(name = "longitude", column = @Column(name = "departure_longitude", nullable = false)),
        @AttributeOverride(name = "placeName", column = @Column(name = "departure_place_name"))
    })
    private Location departure;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "latitude", column = @Column(name = "arrival_latitude", nullable = false)),
        @AttributeOverride(name = "longitude", column = @Column(name = "arrival_longitude", nullable = false)),
        @AttributeOverride(name = "placeName", column = @Column(name = "arrival_place_name"))
    })
    private Location arrival;

    @Builder
    private CommuteProfile(Long memberId, CommuteType commuteType, TransportMode transportMode,
            String frequentRouteName, Integer averageDurationMinutes, Location departure, Location arrival) {
        this.memberId = memberId;
        this.commuteType = commuteType;
        this.transportMode = transportMode;
        this.frequentRouteName = frequentRouteName;
        this.averageDurationMinutes = averageDurationMinutes;
        this.departure = departure;
        this.arrival = arrival;
    }

    public void update(CommuteType commuteType, TransportMode transportMode, String frequentRouteName,
            Integer averageDurationMinutes, Location departure, Location arrival) {
        this.commuteType = commuteType;
        this.transportMode = transportMode;
        this.frequentRouteName = frequentRouteName;
        this.averageDurationMinutes = averageDurationMinutes;
        this.departure = departure;
        this.arrival = arrival;
    }
}
