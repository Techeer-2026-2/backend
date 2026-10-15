package com.techeer.backend.domain.member.entity;

import com.techeer.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 음악 앱 회원. playlists.member_id, commute_profiles.member_id 가 이 테이블의 member_id 를 가리킨다.
 *
 * <p>광고주(advertisers)와는 별개의 계정이다. 두 계정의 토큰은 서로 바꿔 쓸 수 없다(TokenAudience).
 *
 * <p>setter 를 두지 않는다. 값을 바꿀 일이 생기면 그 목적에 맞는 메서드를 추가한다.
 */
@Getter
@Entity
@Table(name = "members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long memberId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 64)
    private String nickname;

    /** 현재 유효한 refresh token 의 SHA-256 해시. 로그아웃 상태이거나 로그인한 적이 없으면 null. */
    @Column(length = 64)
    private String refreshTokenHash;

    private Member(String email, String passwordHash, String nickname) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
    }

    /**
     * 신규 회원을 만든다. passwordHash 는 이미 해시된 값이어야 한다(평문 금지).
     */
    public static Member create(String email, String passwordHash, String nickname) {
        return new Member(email, passwordHash, nickname);
    }

    /**
     * 유효한 refresh token 의 해시를 바꾼다. 새로 로그인하면 이전 토큰은 더 이상 쓸 수 없게 된다.
     */
    public void changeRefreshTokenHash(String refreshTokenHash) {
        this.refreshTokenHash = refreshTokenHash;
    }
}
