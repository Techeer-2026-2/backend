package com.techeer.backend.domain.auth.jwt;

/**
 * 토큰을 쓸 수 있는 대상. 광고주와 음악 앱 회원이 같은 서명 키를 쓰므로, 토큰 안에 대상을 적어 두지 않으면
 * 광고주 5번의 토큰이 회원 5번의 토큰으로 통하게 된다.
 */
public enum TokenAudience {
    ADVERTISER,
    USER
}
