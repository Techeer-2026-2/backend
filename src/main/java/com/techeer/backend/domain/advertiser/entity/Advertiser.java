package com.techeer.backend.domain.advertiser.entity;

import com.techeer.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 광고주(캠페인을 만드는 사람). campaigns.user_id 가 이 테이블의 user_id 를 가리킨다.
 *
 * <p>음악 앱의 일반 유저 테이블(users)과 이름이 겹치지 않도록 테이블 이름을 advertisers 로 둔다.
 * 배너를 보는 쪽은 target_users 이다.
 *
 * <p>setter 를 두지 않는다. 값을 바꿀 일이 생기면 그 목적에 맞는 메서드(예: changeBusinessName)를 추가한다.
 */
@Getter
@Entity
@Table(name = "advertisers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Advertiser extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 128)
    private String businessName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Plan plan;

    /** 현재 유효한 refresh token 의 SHA-256 해시. 로그아웃 상태이거나 로그인한 적이 없으면 null. */
    @Column(length = 64)
    private String refreshTokenHash;

    private Advertiser(String email, String passwordHash, String businessName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.businessName = businessName;
        this.plan = Plan.FREE;
    }

    /**
     * 신규 광고주를 만든다. passwordHash 는 이미 해시된 값이어야 한다(평문 금지).
     */
    public static Advertiser create(String email, String passwordHash, String businessName) {
        return new Advertiser(email, passwordHash, businessName);
    }

    /**
     * 유효한 refresh token 의 해시를 바꾼다. 새로 로그인하면 이전 토큰은 더 이상 쓸 수 없게 된다.
     */
    public void changeRefreshTokenHash(String refreshTokenHash) {
        this.refreshTokenHash = refreshTokenHash;
    }
}
