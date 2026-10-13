package com.techeer.backend.global.resolver;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 붙여서 요청을 보낸 회원의 ID를 주입받는다.
 *
 * <p>아직 로그인 기능이 없어 {@code X-Member-Id} 헤더 값을 그대로 신뢰한다 (검증 없음).
 * 실제 인증이 도입되면 {@link MemberIdArgumentResolver} 내부만 교체하면 된다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface MemberId {
}
