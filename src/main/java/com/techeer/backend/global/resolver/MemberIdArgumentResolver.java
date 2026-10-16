package com.techeer.backend.global.resolver;

import com.techeer.backend.domain.auth.jwt.JwtTokenProvider;
import com.techeer.backend.domain.auth.jwt.TokenType;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link MemberId}가 붙은 파라미터에 요청을 보낸 회원의 ID를 채워준다.
 *
 * <p>식별 순서는 다음과 같다.
 * <ol>
 *   <li>{@code Authorization: Bearer} 토큰이 있으면 회원 access token 을 검증해 ID 를 꺼낸다.
 *       토큰이 잘못됐거나 광고주 토큰이면 INVALID_TOKEN 이며, 이때는 {@code X-Member-Id} 로 대신하지 않는다.</li>
 *   <li>토큰이 없고 {@code auth.legacy-member-header} 가 true 이면 {@code X-Member-Id} 헤더 값을 그대로 신뢰한다(임시).</li>
 *   <li>그 밖에는 로그인이 필요하다는 오류를 낸다.</li>
 * </ol>
 *
 * <p>2번은 프론트가 로그인 화면으로 전환하기 전까지만 쓰는 임시 경로다. 헤더 값만 바꾸면 다른 회원으로 행세할 수 있으므로
 * 전환이 끝나면 {@code AUTH_LEGACY_MEMBER_HEADER=false} 로 끈다. 컨트롤러는 바꿀 필요가 없다.
 *
 * <p>토큰의 서명·만료·종류·대상만 확인하고 DB 는 조회하지 않는다. 탈퇴한 회원의 토큰이 살아 있는 동안은 이 단계를
 * 통과하며, 실제로 회원 정보를 읽는 서비스가 존재 여부를 다시 확인한다.
 */
@Component
public class MemberIdArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String HEADER_NAME = "X-Member-Id";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final boolean legacyMemberHeaderEnabled;

    /**
     * JwtTokenProvider 는 @Lazy 로 받아 실제로 토큰을 검증하는 순간에 가져온다.
     * 웹 계층 테스트({@code @WebMvcTest})는 이 리졸버는 읽지만 일반 {@code @Component} 인 JwtTokenProvider 는 읽지 않기 때문이다.
     */
    public MemberIdArgumentResolver(
            @Lazy JwtTokenProvider jwtTokenProvider,
            @Value("${auth.legacy-member-header:true}") boolean legacyMemberHeaderEnabled) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.legacyMemberHeaderEnabled = legacyMemberHeaderEnabled;
    }

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
        String authorization = webRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            if (token.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }
            return jwtTokenProvider.parseUserId(token, TokenType.ACCESS);
        }

        if (!legacyMemberHeaderEnabled) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
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
