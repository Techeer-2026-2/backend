package com.techeer.backend.domain.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * refresh token 을 DB 에 그대로 저장하지 않고 SHA-256 해시로 저장하기 위한 도구.
 *
 * <p>비밀번호와 달리 refresh token 은 길고 예측할 수 없는 값이라 bcrypt 처럼 느린 해시가 필요 없다.
 * 같은 입력이 항상 같은 해시가 나와야 "저장된 값과 일치하는지"를 DB 에서 바로 비교할 수 있다.
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    public static String sha256Hex(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", e);
        }
    }
}
