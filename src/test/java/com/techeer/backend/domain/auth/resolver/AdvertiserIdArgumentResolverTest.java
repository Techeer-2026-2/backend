package com.techeer.backend.domain.auth.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.techeer.backend.domain.auth.jwt.JwtProperties;
import com.techeer.backend.domain.auth.jwt.JwtTokenProvider;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * AdvertiserIdArgumentResolver 를 스프링 없이 직접 만들어 Authorization 헤더 처리 규칙을 빠르게 검증한다.
 */
class AdvertiserIdArgumentResolverTest {

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(
            new JwtProperties("resolver-test-secret-0123456789-abcdefghij", 30, 14),
            Clock.fixed(Instant.parse("2026-10-07T00:00:00Z"), ZoneOffset.UTC));
    private final AdvertiserIdArgumentResolver resolver = new AdvertiserIdArgumentResolver(jwtTokenProvider);

    @SuppressWarnings("unused")
    private void handler(@AdvertiserId Long annotated, Long plain, @AdvertiserId String wrongType) {
    }

    @Test
    @DisplayName("@AdvertiserId 가 붙은 Long 파라미터만 처리한다")
    void supportsOnlyAnnotatedLong() throws Exception {
        assertThat(resolver.supportsParameter(parameter(0))).isTrue();
        assertThat(resolver.supportsParameter(parameter(1))).isFalse();
        assertThat(resolver.supportsParameter(parameter(2))).isFalse();
    }

    @Test
    @DisplayName("유효한 access token 이면 광고주 ID 를 돌려준다 (Bearer 는 대소문자 구분 없음)")
    void resolvesAdvertiserId() {
        String token = jwtTokenProvider.createAccessToken(42L);

        assertThat(resolve("Bearer " + token)).isEqualTo(42L);
        assertThat(resolve("bearer " + token)).isEqualTo(42L);
        assertThat(resolve("Bearer   " + token + "  ")).isEqualTo(42L);
    }

    @Test
    @DisplayName("헤더가 없거나 Bearer 형식이 아니거나 토큰이 비어 있으면 AUTH_REQUIRED")
    void rejectsMissingCredentials() {
        assertErrorCode(null, ErrorCode.AUTH_REQUIRED);
        assertErrorCode("Basic dXNlcjpwYXNz", ErrorCode.AUTH_REQUIRED);
        assertErrorCode("Bearer", ErrorCode.AUTH_REQUIRED);
        assertErrorCode("Bearer    ", ErrorCode.AUTH_REQUIRED);
        assertErrorCode(jwtTokenProvider.createAccessToken(42L), ErrorCode.AUTH_REQUIRED);
    }

    @Test
    @DisplayName("위조·형식 오류·refresh token 은 INVALID_TOKEN")
    void rejectsInvalidTokens() {
        assertErrorCode("Bearer not-a-jwt", ErrorCode.INVALID_TOKEN);
        assertErrorCode("Bearer " + jwtTokenProvider.createRefreshToken(42L), ErrorCode.INVALID_TOKEN);
    }

    private Object resolve(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return resolver.resolveArgument(parameter(0), null, new ServletWebRequest(request), null);
    }

    private void assertErrorCode(String authorization, ErrorCode expected) {
        assertThatThrownBy(() -> resolve(authorization))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private MethodParameter parameter(int index) {
        try {
            Method method = AdvertiserIdArgumentResolverTest.class.getDeclaredMethod(
                    "handler", Long.class, Long.class, String.class);
            return new MethodParameter(method, index);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }
}
