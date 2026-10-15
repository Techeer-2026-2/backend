package com.techeer.backend.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * JwtTokenProvider 를 스프링 없이 직접 만들어 검증한다. 시각은 고정 Clock 으로 제어해서 만료를 재현한다.
 */
class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-jwt-secret-0123456789-abcdefghij";
    private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");

    private final JwtTokenProvider provider = providerAt(NOW);

    @Test
    @DisplayName("발급한 토큰에서 같은 광고주 ID 를 꺼낼 수 있다")
    void roundTrip() {
        assertThat(provider.parseAdvertiserId(provider.createAccessToken(7L), TokenType.ACCESS)).isEqualTo(7L);
        assertThat(provider.parseAdvertiserId(provider.createRefreshToken(7L), TokenType.REFRESH)).isEqualTo(7L);
    }

    @Test
    @DisplayName("같은 광고주에게 같은 순간 발급해도 refresh token 은 매번 다르다")
    void refreshTokensAreUnique() {
        assertThat(provider.createRefreshToken(7L)).isNotEqualTo(provider.createRefreshToken(7L));
    }

    @Test
    @DisplayName("종류가 다른 토큰은 거절한다 (access 를 refresh 로, refresh 를 access 로 쓸 수 없다)")
    void rejectsWrongType() {
        String access = provider.createAccessToken(7L);
        String refresh = provider.createRefreshToken(7L);

        assertInvalid(() -> provider.parseAdvertiserId(access, TokenType.REFRESH));
        assertInvalid(() -> provider.parseAdvertiserId(refresh, TokenType.ACCESS));
    }

    @Test
    @DisplayName("access token 은 30분이 지나면, refresh token 은 14일이 지나면 만료된다")
    void rejectsExpired() {
        String access = provider.createAccessToken(7L);
        String refresh = provider.createRefreshToken(7L);

        JwtTokenProvider after29Minutes = providerAt(NOW.plus(Duration.ofMinutes(29)));
        assertThat(after29Minutes.parseAdvertiserId(access, TokenType.ACCESS)).isEqualTo(7L);

        JwtTokenProvider after31Minutes = providerAt(NOW.plus(Duration.ofMinutes(31)));
        assertInvalid(() -> after31Minutes.parseAdvertiserId(access, TokenType.ACCESS));
        assertThat(after31Minutes.parseAdvertiserId(refresh, TokenType.REFRESH)).isEqualTo(7L);

        JwtTokenProvider after15Days = providerAt(NOW.plus(Duration.ofDays(15)));
        assertInvalid(() -> after15Days.parseAdvertiserId(refresh, TokenType.REFRESH));
    }

    @Test
    @DisplayName("다른 키로 서명했거나 내용을 고친 토큰, 이상한 문자열은 모두 INVALID_TOKEN")
    void rejectsForgedAndGarbage() {
        JwtTokenProvider otherKey = new JwtTokenProvider(
                new JwtProperties("another-secret-key-0123456789-abcdefghijklmnop", 30, 14),
                Clock.fixed(NOW, ZoneOffset.UTC));
        String forged = otherKey.createAccessToken(7L);
        assertInvalid(() -> provider.parseAdvertiserId(forged, TokenType.ACCESS));

        String valid = provider.createAccessToken(7L);
        String tampered = valid.substring(0, valid.length() - 2) + (valid.endsWith("AA") ? "BB" : "AA");
        assertInvalid(() -> provider.parseAdvertiserId(tampered, TokenType.ACCESS));

        assertInvalid(() -> provider.parseAdvertiserId("not-a-jwt", TokenType.ACCESS));
        assertInvalid(() -> provider.parseAdvertiserId("", TokenType.ACCESS));
    }

    @Test
    @DisplayName("서명 키가 32바이트보다 짧거나 비어 있으면 무엇을 고쳐야 하는지 알려 주며 만들 수 없다")
    void rejectsWeakKey() {
        for (String weak : new String[] {"too-short", "", null}) {
            assertThatThrownBy(() -> new JwtTokenProvider(
                    new JwtProperties(weak, 30, 14), Clock.fixed(NOW, ZoneOffset.UTC)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_SECRET");
        }
    }

    @Test
    @DisplayName("회원 토큰에서 회원 ID 를 꺼낼 수 있다")
    void memberRoundTrip() {
        assertThat(provider.parseMemberId(provider.createMemberAccessToken(9L), TokenType.ACCESS)).isEqualTo(9L);
        assertThat(provider.parseMemberId(provider.createMemberRefreshToken(9L), TokenType.REFRESH)).isEqualTo(9L);
    }

    @Test
    @DisplayName("광고주 토큰은 회원 토큰으로, 회원 토큰은 광고주 토큰으로 쓸 수 없다")
    void audiencesAreNotInterchangeable() {
        String advertiserAccess = provider.createAccessToken(5L);
        String advertiserRefresh = provider.createRefreshToken(5L);
        String memberAccess = provider.createMemberAccessToken(5L);
        String memberRefresh = provider.createMemberRefreshToken(5L);

        assertInvalid(() -> provider.parseMemberId(advertiserAccess, TokenType.ACCESS));
        assertInvalid(() -> provider.parseMemberId(advertiserRefresh, TokenType.REFRESH));
        assertInvalid(() -> provider.parseAdvertiserId(memberAccess, TokenType.ACCESS));
        assertInvalid(() -> provider.parseAdvertiserId(memberRefresh, TokenType.REFRESH));
    }

    @Test
    @DisplayName("대상(aud) 구분이 생기기 전에 발급된 광고주 토큰은 광고주용으로만 통한다")
    void legacyTokenWithoutAudienceIsAdvertiserOnly() {
        String legacy = Jwts.builder()
                .id("legacy-1")
                .subject("5")
                .claim("type", "ACCESS")
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plus(Duration.ofMinutes(30))))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(provider.parseAdvertiserId(legacy, TokenType.ACCESS)).isEqualTo(5L);
        assertInvalid(() -> provider.parseMemberId(legacy, TokenType.ACCESS));
    }

    private static JwtTokenProvider providerAt(Instant now) {
        return new JwtTokenProvider(new JwtProperties(SECRET, 30, 14), Clock.fixed(now, ZoneOffset.UTC));
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }
}
