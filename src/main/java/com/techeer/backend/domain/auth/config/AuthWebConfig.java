package com.techeer.backend.domain.auth.config;

import com.techeer.backend.domain.auth.resolver.AdvertiserId;
import com.techeer.backend.domain.auth.resolver.AdvertiserIdArgumentResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 광고주 인증용 인자 리졸버를 Spring MVC 에 등록한다.
 *
 * <p>다른 웹 설정(WebConfig 등)과 충돌하지 않도록 인증 전용 설정 클래스로 따로 뒀다.
 */
@Configuration
@RequiredArgsConstructor
public class AuthWebConfig implements WebMvcConfigurer {

    static {
        // Swagger 문서에 @AdvertiserId 파라미터(토큰에서 채워지므로 요청자가 보내는 값이 아님)가 보이지 않게 한다.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(AdvertiserId.class);
    }

    private final AdvertiserIdArgumentResolver advertiserIdArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(advertiserIdArgumentResolver);
    }
}
