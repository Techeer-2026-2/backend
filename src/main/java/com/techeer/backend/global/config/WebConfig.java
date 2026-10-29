package com.techeer.backend.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.backend.global.resolver.MemberIdArgumentResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 전역 설정. {@link MemberIdArgumentResolver}와 {@link MultipartJsonPartConverter}를 스프링에 등록한다.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final MemberIdArgumentResolver memberIdArgumentResolver;
    private final ObjectMapper objectMapper;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(memberIdArgumentResolver);
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 맨 뒤에 붙여 기본 변환기가 먼저 고르게 한다.
        converters.add(new MultipartJsonPartConverter(objectMapper));
    }
}
