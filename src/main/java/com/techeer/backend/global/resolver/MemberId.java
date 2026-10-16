package com.techeer.backend.global.resolver;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 붙여서 요청을 보낸 회원의 ID를 주입받는다.
 *
 * <p>로그인 토큰(Authorization: Bearer)으로 식별하고, 프론트 전환 전까지는 설정에 따라 {@code X-Member-Id} 헤더도
 * 임시로 받는다. 자세한 규칙은 {@link MemberIdArgumentResolver}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface MemberId {
}
