package com.techeer.backend.domain.auth.resolver;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 붙이면 요청의 access token(Authorization: Bearer ...)을 검증해 로그인한 광고주의 ID 를 넣어 준다.
 *
 * <p>토큰이 없거나 올바르지 않으면 컨트롤러 메서드가 실행되기 전에 401 로 응답한다.
 * 이 표시를 붙인 API 만 인증이 필요하고, 붙이지 않은 API(배너·클릭 등)는 지금처럼 공개 상태다.
 * 사용 예: {@code public AdvertiserResponse getMe(@AdvertiserId Long advertiserId)}
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdvertiserId {
}
