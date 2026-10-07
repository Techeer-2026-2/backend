package com.techeer.backend.domain.auth.resolver;

import com.techeer.backend.domain.auth.jwt.JwtTokenProvider;
import com.techeer.backend.domain.auth.jwt.TokenType;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link AdvertiserId} 가 붙은 파라미터에 access token 에서 꺼낸 광고주 ID 를 넣는다.
 *
 * <p>토큰의 서명·만료·종류(access)만 확인하고 DB 는 조회하지 않는다. 그래서 탈퇴한 광고주라도 토큰이 살아 있는 동안은
 * 이 단계를 통과하며, 실제 조회·수정을 하는 서비스가 존재 여부(404)를 다시 확인한다.
 */
@Component
public class AdvertiserIdArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * JwtTokenProvider 는 @Lazy 로 받아 실제로 토큰을 검증하는 순간에 가져온다.
     *
     * <p>{@code @WebMvcTest} 같은 웹 계층 테스트는 HandlerMethodArgumentResolver 는 읽지만 일반 {@code @Component} 인
     * JwtTokenProvider 는 읽지 않는다. 생성 시점에 필수로 요구하면 인증과 무관한 컨트롤러 테스트(헬스체크 등)까지 깨진다.
     */
    public AdvertiserIdArgumentResolver(@Lazy JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        Class<?> type = parameter.getParameterType();
        return parameter.hasParameterAnnotation(AdvertiserId.class)
                && (Long.class.equals(type) || long.class.equals(type));
    }

    /**
     * Authorization 헤더에서 토큰을 꺼내 검증하고 광고주 ID 를 돌려준다.
     *
     * @throws BusinessException 헤더가 없거나 Bearer 형식이 아니면 AUTH_REQUIRED, 토큰이 올바르지 않으면 INVALID_TOKEN
     */
    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        String header = webRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        return jwtTokenProvider.parseAdvertiserId(token, TokenType.ACCESS);
    }
}
