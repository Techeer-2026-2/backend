package com.techeer.backend.domain.owner.entity;

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
 * 광고주(캠페인을 만드는 사람). campaigns.user_id 가 이 테이블의 user_id 를 가리킨다.
 *
 * <p>setter 를 두지 않는다. 값을 바꿀 일이 생기면 그 목적에 맞는 메서드(예: changeBusinessName)를 추가한다.
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Owner extends BaseEntity {

    private static final String DEFAULT_PLAN = "free";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 128)
    private String businessName;

    @Column(nullable = false, length = 16)
    private String plan;

    private Owner(String email, String passwordHash, String businessName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.businessName = businessName;
        this.plan = DEFAULT_PLAN;
    }

    /**
     * 신규 광고주를 만든다. passwordHash 는 이미 해시된 값이어야 한다(평문 금지).
     */
    public static Owner create(String email, String passwordHash, String businessName) {
        return new Owner(email, passwordHash, businessName);
    }
}
