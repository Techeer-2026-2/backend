package com.techeer.backend.global.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;

class MemberIdArgumentResolverTest {

    private final MemberIdArgumentResolver resolver = new MemberIdArgumentResolver();

    @Test
    void supportsParameter는_MemberId가_붙은_Long_파라미터만_지원한다() throws NoSuchMethodException {
        assertThat(resolver.supportsParameter(parameterOf("withMemberId", Long.class))).isTrue();
        assertThat(resolver.supportsParameter(parameterOf("withoutAnnotation", Long.class))).isFalse();
        assertThat(resolver.supportsParameter(parameterOf("withWrongType", String.class))).isFalse();
    }

    @Test
    void 헤더가_있으면_Long으로_변환해_반환한다() throws NoSuchMethodException {
        NativeWebRequest request = mock(NativeWebRequest.class);
        when(request.getHeader("X-Member-Id")).thenReturn("42");

        Object result = resolver.resolveArgument(parameterOf("withMemberId", Long.class), null, request, null);

        assertThat(result).isEqualTo(42L);
    }

    @Test
    void 헤더가_없으면_MISSING_MEMBER_ID_예외를_던진다() throws NoSuchMethodException {
        NativeWebRequest request = mock(NativeWebRequest.class);
        when(request.getHeader("X-Member-Id")).thenReturn(null);

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> resolver.resolveArgument(parameterOf("withMemberId", Long.class), null, request, null));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MISSING_MEMBER_ID);
    }

    @Test
    void 헤더가_숫자가_아니면_INVALID_MEMBER_ID_예외를_던진다() throws NoSuchMethodException {
        NativeWebRequest request = mock(NativeWebRequest.class);
        when(request.getHeader("X-Member-Id")).thenReturn("abc");

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> resolver.resolveArgument(parameterOf("withMemberId", Long.class), null, request, null));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_MEMBER_ID);
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
