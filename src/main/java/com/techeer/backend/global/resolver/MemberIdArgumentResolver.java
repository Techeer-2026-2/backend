package com.techeer.backend.global.resolver;

import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link MemberId}가 붙은 파라미터에 {@code X-Member-Id} 헤더 값을 채워준다.
 *
 * <p>TODO(인증): 지금은 헤더 값을 검증 없이 그대로 신뢰하는 임시 방식이다.
 * 실제 인증이 도입되면 이 클래스만 교체하면 된다 (컨트롤러는 변경 불필요).
 */
public class MemberIdArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String HEADER_NAME = "X-Member-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(MemberId.class)
                && parameter.getParameterType().equals(Long.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        String header = webRequest.getHeader(HEADER_NAME);
        if (header == null || header.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_MEMBER_ID);
        }

        try {
            return Long.parseLong(header);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_MEMBER_ID);
        }
    }
}
