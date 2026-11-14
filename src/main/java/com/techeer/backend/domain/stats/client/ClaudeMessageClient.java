package com.techeer.backend.domain.stats.client;

import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Claude Messages API 를 한 번 호출해서 텍스트 답을 돌려준다.
 * 키(ANTHROPIC_API_KEY)가 비어 있으면 호출하지 않고 AI_NOT_CONFIGURED 를 던진다.
 */
@Component
public class ClaudeMessageClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 2048;

    private final RestClient claudeRestClient;
    private final String apiKey;
    private final String model;

    public ClaudeMessageClient(
            @Qualifier("claudeRestClient") RestClient claudeRestClient,
            @Value("${external.claude.api-key:}") String apiKey,
            @Value("${external.claude.model}") String model) {
        this.claudeRestClient = claudeRestClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * @param prompt 사용자 메시지 한 개
     * @return 모델이 보낸 첫 번째 텍스트 블록
     */
    public String complete(String prompt) {
        if (!isConfigured()) {
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED);
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", MAX_TOKENS,
                "messages", List.of(Map.of("role", "user", "content", prompt)));
        try {
            MessageResponse response = claudeRestClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MessageResponse.class);
            // 모델은 생각(thinking) 블록을 먼저 보낼 수 있다. 실제 답은 type 이 text 인 블록에 있다.
            String text = response == null || response.content() == null ? null
                    : response.content().stream()
                            .filter(block -> "text".equals(block.type()) && block.text() != null)
                            .map(ContentBlock::text)
                            .findFirst()
                            .orElse(null);
            if (text == null) {
                throw new BusinessException(ErrorCode.AI_CALL_FAILED);
            }
            return text.trim();
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.AI_CALL_FAILED, e);
        }
    }

    /** 응답 중 필요한 부분만 읽는다. 나머지 필드는 무시한다. */
    record MessageResponse(List<ContentBlock> content) {
    }

    record ContentBlock(String type, String text) {
    }
}
