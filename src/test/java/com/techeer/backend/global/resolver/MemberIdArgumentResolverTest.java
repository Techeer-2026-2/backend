package com.techeer.backend.global.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.techeer.backend.domain.auth.jwt.JwtTokenProvider;
import com.techeer.backend.domain.auth.jwt.TokenType;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;

class MemberIdArgumentResolverTest {

    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final MemberIdArgumentResolver resolver = new MemberIdArgumentResolver(jwtTokenProvider, true);
    private final MemberIdArgumentResolver strictResolver = new MemberIdArgumentResolver(jwtTokenProvider, false);

    @Test
    void supportsParameter는_MemberId가_붙은_Long_파라미터만_지원한다() throws NoSuchMethodException {
        assertThat(resolver.supportsParameter(parameterOf("withMemberId", Long.class))).isTrue();
        assertThat(resolver.supportsParameter(parameterOf("withoutAnnotation", Long.class))).isFalse();
        assertThat(resolver.supportsParameter(parameterOf("withWrongType", String.class))).isFalse();
    }

    @Test
    void 토큰이_없고_헤더가_있으면_Long으로_변환해_반환한다() throws NoSuchMethodException {
        NativeWebRequest request = mock(NativeWebRequest.class);
        when(request.getHeader("X-Member-Id")).thenReturn("42");

        Object result = resolver.resolveArgument(parameterOf("withMemberId", Long.class), null, request, null);

        assertThat(result).isEqualTo(42L);
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void 토큰도_헤더도_없으면_MISSING_MEMBER_ID_예외를_던진다() throws NoSuchMethodException {
        NativeWebRequest request = mock(NativeWebRequest.class);

        assertThat(errorOf(resolver, request)).isEqualTo(ErrorCode.MISSING_MEMBER_ID);
    }

    @Test
    void 헤더가_숫자가_아니면_INVALID_MEMBER_ID_예외를_던진다() throws NoSuchMethodException {
        NativeWebRequest request = mock(NativeWebRequest.class);
        when(request.getHeader("X-Member-Id")).thenReturn("abc");

        assertThat(errorOf(resolver, request)).isEqualTo(ErrorCode.INVALID_MEMBER_ID);
    }

    @Test
    void Bearer_토큰이_있으면_토큰에서_회원_ID를_꺼낸다() throws NoSuchMethodException {
        NativeWebRequest request = mock(NativeWebRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer member-token");
        when(jwtTokenProvider.parseUserId("member-token", TokenType.ACCESS)).thenReturn(7L);

        Object result = resolver.resolveArgument(parameterOf("withMemberId", Long.class), null, request, null);

        assertThat(result).isEqualTo(7L);
    }

    @Test
    void 토큰과_헤더가_둘_다_있으면_토큰이_우선한다() throws NoSuchMethodException {
        NativeWebRequest request = mock(NativeWebRequest.class);
        when(request.getHeader("Authorization")).thenReturn("bearer member-token");
        when(request.getHeader("X-Member-Id")).thenReturn("99");
        when(jwtTokenProvider.parseUserId("member-token", TokenType.ACCESS)).thenReturn(7L);

        Object result = resolver.resolveArgument(parameterOf("withMemberId", Long.class), null, request, null);

        assertThat(result).isEqualTo(7L);
    }

    @Test
    void 토큰이_잘못되면_헤더가_있어도_대신하지_않고_INVALID_TOKEN_예외를_던진다() throws NoSuchMethodException {
        NativeWebRequest request = mock(NativeWebRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer broken");
        when(request.getHeader("X-Member-Id")).thenReturn("99");
        when(jwtTokenProvider.parseUserId("broken", TokenType.ACCESS))
                .thenThrow(new BusinessException(ErrorCode.INVALID_TOKEN));

        assertThat(errorOf(resolver, request)).isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void Bearer_뒤가_비어_있으면_INVALID_TOKEN_예외를_던진다() throws NoSuchMethodException {
        NativeWebRequest request = mock(NativeWebRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer   ");
        when(request.getHeader("X-Member-Id")).thenReturn("99");

        assertThat(errorOf(resolver, request)).isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void Bearer_형식이_아닌_Authorization_은_없는_것으로_보고_헤더를_본다() throws NoSuchMethodException {
        NativeWebRequest request = mock(NativeWebRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Basic abc");
        when(request.getHeader("X-Member-Id")).thenReturn("5");

        Object result = resolver.resolveArgument(parameterOf("withMemberId", Long.class), null, request, null);

        assertThat(result).isEqualTo(5L);
    }

    @Test
    void 임시_헤더를_끄면_헤더만_있는_요청은_AUTH_REQUIRED_예외를_던진다() throws NoSuchMethodException {
        NativeWebRequest request = mock(NativeWebRequest.class);
        when(request.getHeader("X-Member-Id")).thenReturn("42");

        assertThat(errorOf(strictResolver, request)).isEqualTo(ErrorCode.AUTH_REQUIRED);
    }

    @Test
    void 임시_헤더를_꺼도_토큰은_받는다() throws NoSuchMethodException {
        NativeWebRequest request = mock(NativeWebRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer member-token");
        when(jwtTokenProvider.parseUserId("member-token", TokenType.ACCESS)).thenReturn(7L);

        Object result = strictResolver.resolveArgument(parameterOf("withMemberId", Long.class), null, request, null);

        assertThat(result).isEqualTo(7L);
    }

    private ErrorCode errorOf(MemberIdArgumentResolver target, NativeWebRequest request) throws NoSuchMethodException {
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> target.resolveArgument(parameterOf("withMemberId", Long.class), null, request, null));
        return exception.getErrorCode();
    }

    private MethodParameter parameterOf(String methodName, Class<?> paramType) throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod(methodName, paramType);
        return new MethodParameter(method, 0);
    }

    private void withMemberId(@MemberId Long memberId) {
    }

    private void withoutAnnotation(Long memberId) {
    }

    private void withWrongType(@MemberId String memberId) {
    }
}
