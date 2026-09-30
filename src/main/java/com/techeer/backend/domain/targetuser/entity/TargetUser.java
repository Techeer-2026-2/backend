package com.techeer.backend.domain.targetuser.entity;

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

/**
 * 배너를 보는 (가상) 유저. age_group 으로 캠페인과 매칭된다.
 */
@Getter
@Entity
@Table(name = "target_users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TargetUser extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long targetUserId;

    @Column(nullable = false, length = 16)
    private String ageGroup;

    @Column(length = 8)
    private String gender;

    @Builder
    private TargetUser(String ageGroup, String gender) {
        this.ageGroup = ageGroup;
        this.gender = gender;
    }
}
