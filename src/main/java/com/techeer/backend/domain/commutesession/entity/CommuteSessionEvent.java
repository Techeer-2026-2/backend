package com.techeer.backend.domain.commutesession.entity;

import com.techeer.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 통근 중 곡에 일어난 일 하나. 앱이 정한 {@code eventId} 로 같은 이벤트를 다시 보내도 한 번만 기록한다(중복 방지).
 * 행은 {@code CommuteSessionEventRepository#insertIfAbsent} 로 넣으므로 이 엔티티는 읽기용이다.
 */
@Getter
@Entity
@Table(name = "commute_session_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommuteSessionEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "itunes_track_id", nullable = false)
    private Long itunesTrackId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 10)
    private PlaybackAction action;

    /** 그 곡을 실제로 들은 시간(초). 모르면 null */
    @Column(name = "played_seconds")
    private Integer playedSeconds;
}
