package com.techeer.backend.domain.auth.jwt;

/**
 * 토큰 종류. access token 을 refresh 자리에(또는 반대로) 쓰지 못하도록 토큰 안에 종류를 적어 둔다.
 */
public enum TokenType {
    ACCESS,
    REFRESH
}
