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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 통근 한 번의 기록. 시작하면 ACTIVE, 종료(COMPLETED)나 취소(CANCELED)로 끝난다.
 *
 * <p>회원당 진행 중인 통근은 하나뿐이다. 이를 DB 가 보장하도록 {@code activeMemberId} 를 두었다. 진행 중일 때만 회원 ID 가
 * 들어 있고 끝나면 null 이 되며, 이 칼럼에 unique 제약이 있어서 같은 회원이 동시에 두 번 시작해도 한쪽은 제약 위반으로 실패한다.
 * (NULL 은 unique 제약에서 서로 다른 값으로 취급되므로 끝난 세션은 몇 개든 쌓인다.)
 */
@Getter
@Entity
@Table(name = "commute_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommuteSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long commuteProfileId;

    /** 통근 중 들을 플레이리스트. 정하지 않았으면 null */
    private Long playlistId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommuteSessionStatus status;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    /** 시작할 때의 예상 소요시간(분). 통근 경로를 모르면 null */
    private Integer expectedDurationMinutes;

    @Column(unique = true)
    private Long activeMemberId;

    private CommuteSession(Long memberId, Long commuteProfileId, Long playlistId, LocalDateTime startedAt,
            Integer expectedDurationMinutes) {
        this.memberId = memberId;
        this.commuteProfileId = commuteProfileId;
        this.playlistId = playlistId;
        this.status = CommuteSessionStatus.ACTIVE;
        this.startedAt = startedAt;
        this.expectedDurationMinutes = expectedDurationMinutes;
        this.activeMemberId = memberId;
    }

    public static CommuteSession start(Long memberId, Long commuteProfileId, Long playlistId, LocalDateTime now,
            Integer expectedDurationMinutes) {
        return new CommuteSession(memberId, commuteProfileId, playlistId, now, expectedDurationMinutes);
    }

    public boolean isActive() {
        return status == CommuteSessionStatus.ACTIVE;
    }
}
