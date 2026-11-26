package com.techeer.backend.domain.user.entity;

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
 * 음악 앱 회원(ERD 의 users). playlists.member_id, commute_profiles.member_id 가 이 테이블의 user_id 값을 담는다
 * (두 테이블의 컬럼 이름이 member_id 이고 FK 제약은 없다).
 *
 * <p>광고주(advertisers)와는 별개의 계정이다. 두 계정의 토큰은 서로 바꿔 쓸 수 없다(TokenAudience).
 *
 * <p>setter 를 두지 않는다. 값을 바꿀 일이 생기면 그 목적에 맞는 메서드를 추가한다.
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String nickname;

    /** 현재 유효한 refresh token 의 SHA-256 해시. 로그아웃 상태이거나 로그인한 적이 없으면 null. */
    @Column(length = 64)
    private String refreshTokenHash;

    /** 연령대 (10s~50s). 배너 매칭에 쓴다. 입력하지 않았으면 null 이고, 그동안은 배너를 보여 주지 않는다. */
    @Column(length = 16)
    private String ageGroup;

    /** 성별 (male, female). 입력하지 않았으면 null 이고, 성별 제한이 없는(all) 캠페인만 맞는다. */
    @Column(length = 8)
    private String gender;

    private User(String email, String passwordHash, String nickname) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
    }

    /**
     * 신규 회원을 만든다. passwordHash 는 이미 해시된 값이어야 한다(평문 금지).
     */
    public static User create(String email, String passwordHash, String nickname) {
        return new User(email, passwordHash, nickname);
    }

    /**
     * 유효한 refresh token 의 해시를 바꾼다. 새로 로그인하면 이전 토큰은 더 이상 쓸 수 없게 된다.
     */
    public void changeRefreshTokenHash(String refreshTokenHash) {
        this.refreshTokenHash = refreshTokenHash;
    }

    /**
     * 닉네임을 바꾼다. 이메일·비밀번호 같은 다른 값은 이 메서드로 바꿀 수 없다.
     */
    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * 배너 매칭에 쓰는 연령대·성별을 바꾼다. null 인 값은 그대로 둔다. 값이 허용 목록에 있는지는 요청 DTO 가 검증한다.
     */
    public void changeDemographics(String ageGroup, String gender) {
        if (ageGroup != null) {
            this.ageGroup = ageGroup;
        }
        if (gender != null) {
            this.gender = gender;
        }
    }
}
