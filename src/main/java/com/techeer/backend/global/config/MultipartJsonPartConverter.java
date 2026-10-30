package com.techeer.backend.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;

/**
 * multipart 요청의 JSON 파트({@code @RequestPart})를 Content-Type 없이 보내도 읽게 한다.
 *
 * <p>브라우저 FormData 에 JSON 을 문자열로 넣거나 Swagger UI 로 보내면 그 파트에는 Content-Type 이 없어
 * application/octet-stream 으로 취급되고, 기본 설정으로는 415 가 난다. 읽기 전용이며 응답을 쓰는 데는 쓰지 않는다.
 * {@link WebConfig} 가 기본 변환기 뒤에 등록하므로 byte[]·String 처럼 원래 octet-stream 을 읽던 경우는 영향이 없다.
 */
public class MultipartJsonPartConverter extends AbstractJackson2HttpMessageConverter {

    public MultipartJsonPartConverter(ObjectMapper objectMapper) {
        super(objectMapper, MediaType.APPLICATION_OCTET_STREAM);
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return false;
    }

    @Override
    public boolean canWrite(Type type, Class<?> clazz, MediaType mediaType) {
        return false;
    }

    @Override
    protected boolean canWrite(MediaType mediaType) {
        return false;
    }
}
