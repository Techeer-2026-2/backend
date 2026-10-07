package com.techeer.backend.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
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

    /** 보호된 API 의 @SecurityRequirement(name = ...) 에 쓰는 보안 방식 이름. */
    public static final String BEARER_AUTH = "bearerAuth";

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

        // 광고주 API 는 Swagger 의 Authorize 버튼에 로그인으로 받은 access token 을 넣어 호출한다.
        SecurityScheme bearerAuth = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .components(new Components().addSecuritySchemes(BEARER_AUTH, bearerAuth))
                .info(info)
                .servers(List.of(new Server().url(serverUrl).description("현재 환경")));
    }
}
