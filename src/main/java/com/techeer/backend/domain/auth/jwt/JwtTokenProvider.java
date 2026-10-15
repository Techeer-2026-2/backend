package com.techeer.backend.domain.auth.jwt;

import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 를 만들고 검증한다. 서명은 HMAC(HS256)이며 키가 32바이트보다 짧으면 앱이 시작되지 않는다.
 *
 * <p>토큰에는 주체 ID(sub), 종류(type), 대상(aud), 발급·만료 시각, 토큰마다 다른 임의 값(jti)만 담는다.
 * jti 가 없으면 같은 초에 같은 주체에게 발급한 refresh token 이 서로 똑같아져 교체 여부를 가릴 수 없다.
 *
 * <p>광고주와 음악 앱 회원은 같은 서명 키를 쓴다. 그래서 대상(aud)으로 구분하고, 검증할 때 대상이 맞는지 확인한다.
 * 대상 구분이 생기기 전에 발급된 광고주 토큰에는 aud 가 없다. 이 토큰들이 만료될 때까지 로그인이 풀리지 않도록,
 * aud 가 없는 토큰은 광고주용으로 본다. 회원 토큰은 aud 가 반드시 USER 여야 한다.
 */
@Component
public class JwtTokenProvider {

    private static final String TYPE_CLAIM = "type";
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey key;

    public JwtTokenProvider(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(secretBytes(properties.secret()));
    }

    /**
     * 서명 키가 너무 짧으면 앱이 시작되지 않게 하고, 무엇을 고쳐야 하는지 알 수 있는 메시지를 남긴다.
     * 운영에서 JWT_SECRET 환경변수를 빠뜨리면 이 메시지가 보인다.
     */
    private static byte[] secretBytes(String secret) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret 은 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다(현재 " + bytes.length
                            + "바이트). 운영에서는 JWT_SECRET 환경변수를 설정하세요.");
        }
        return bytes;
    }

    public String createAccessToken(Long advertiserId) {
        return create(advertiserId, TokenType.ACCESS, TokenAudience.ADVERTISER,
                Duration.ofMinutes(properties.accessTokenMinutes()));
    }

    public String createRefreshToken(Long advertiserId) {
        return create(advertiserId, TokenType.REFRESH, TokenAudience.ADVERTISER,
                Duration.ofDays(properties.refreshTokenDays()));
    }

    public String createUserAccessToken(Long userId) {
        return create(userId, TokenType.ACCESS, TokenAudience.USER,
                Duration.ofMinutes(properties.accessTokenMinutes()));
    }

    public String createUserRefreshToken(Long userId) {
        return create(userId, TokenType.REFRESH, TokenAudience.USER,
                Duration.ofDays(properties.refreshTokenDays()));
    }

    /**
     * 응답의 expiresIn 에 쓰는 access token 유효 시간(초).
     */
    public long accessTokenExpiresInSeconds() {
        return Duration.ofMinutes(properties.accessTokenMinutes()).toSeconds();
    }

    /**
     * 토큰의 서명·만료·종류를 검증하고 광고주 ID 를 꺼낸다.
     *
     * @param token 검증할 토큰
     * @param expectedType 기대하는 토큰 종류
     * @return 토큰에 담긴 광고주 ID
     * @throws BusinessException 위조·만료·형식 오류·종류 불일치 모두 같은 INVALID_TOKEN 으로 던진다
     */
    public Long parseAdvertiserId(String token, TokenType expectedType) {
        return parseSubjectId(token, expectedType, TokenAudience.ADVERTISER);
    }

    /**
     * 음악 앱 회원 토큰을 검증하고 회원 ID 를 꺼낸다. 광고주 토큰이나 대상이 없는 토큰은 거절한다.
     *
     * @throws BusinessException 위조·만료·형식 오류·종류 불일치·대상 불일치 모두 같은 INVALID_TOKEN 으로 던진다
     */
    public Long parseUserId(String token, TokenType expectedType) {
        return parseSubjectId(token, expectedType, TokenAudience.USER);
    }

    private Long parseSubjectId(String token, TokenType expectedType, TokenAudience expectedAudience) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!expectedType.name().equals(claims.get(TYPE_CLAIM, String.class))
                    || !audienceMatches(claims.getAudience(), expectedAudience)) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            // 실패 이유(만료인지 위조인지)는 응답으로 구분해 주지 않는다. 공격자에게 단서가 되기 때문이다.
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    /**
     * 토큰의 대상이 기대한 대상인지 확인한다. aud 가 없는 옛 토큰은 광고주용으로만 인정한다.
     */
    private static boolean audienceMatches(Set<String> audience, TokenAudience expected) {
        if (audience == null || audience.isEmpty()) {
            return expected == TokenAudience.ADVERTISER;
        }
        return audience.size() == 1 && audience.contains(expected.name());
    }

    private String create(Long subjectId, TokenType type, TokenAudience audience, Duration lifetime) {
        Instant now = clock.instant();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(subjectId))
                .claim(TYPE_CLAIM, type.name())
                .audience().add(audience.name()).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(key)
                .compact();
    }
}
