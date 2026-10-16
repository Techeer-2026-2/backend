package com.techeer.backend.domain.music.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

@Configuration
public class ItunesClientConfig {

    /** iTunes 는 JSON 본문을 Content-Type: text/javascript 로 내려준다. 기본 설정은 이걸 JSON 으로 읽지 못한다. */
    private static final MediaType TEXT_JAVASCRIPT = new MediaType("text", "javascript");

    @Bean
    public RestClient itunesRestClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${external.itunes.base-url}") String baseUrl) {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        converter.setSupportedMediaTypes(List.of(MediaType.APPLICATION_JSON, TEXT_JAVASCRIPT));
        return builder
                .baseUrl(baseUrl)
                .messageConverters(converters -> converters.add(0, converter))
                .build();
    }
}
