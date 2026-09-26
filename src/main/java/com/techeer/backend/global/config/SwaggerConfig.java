package com.techeer.backend.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger(OpenAPI) 문서 설정.
 *
 * <p>UI: /swagger-ui.html, 스펙: /v3/api-docs
 */
@Configuration
public class SwaggerConfig {

    @Value("${external.swagger.server-url:http://localhost:8080}")
    private String serverUrl;

    @Bean
    public OpenAPI commutePlaylistOpenAPI() {
        Info info = new Info()
                .title("통근길 플레이리스트 API")
                .description("""
                        통근 정보에 맞춘 음악 추천과 연령대 타겟 배너 광고를 제공하는 API.

                        - 음악: iTunes Search API 프록시 (30초 미리듣기)
                        - 광고: 캠페인 CRUD, 노출(impression)·클릭(click) 기록
                        - 클릭 집계는 notification_id 기반 멱등성으로 중복을 막는다
                        """)
                .version("v0.0.1");

        return new OpenAPI()
                .info(info)
                .servers(List.of(new Server().url(serverUrl).description("현재 환경")));
    }
}
