package com.techeer.backend.domain.stats.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ClaudeMessageClientTest {

    private static final String BASE = "https://api.anthropic.com";

    @Test
    @DisplayName("Messages API 를 올바른 헤더와 본문으로 호출하고 첫 텍스트를 돌려준다")
    void callsMessagesApiAndReturnsText() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ClaudeMessageClient client = new ClaudeMessageClient(builder.build(), "test-key", "claude-sonnet-5-5");

        server.expect(requestTo(BASE + "/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "test-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(jsonPath("$.model").value("claude-sonnet-5-5"))
                .andExpect(jsonPath("$.max_tokens").value(400))
                .andRespond(withSuccess("""
                        {"content":[{"type":"text","text":"  요약 문장입니다.  "}],"stop_reason":"end_turn"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.complete("질문")).isEqualTo("요약 문장입니다.");
        server.verify();
    }

    @Test
    @DisplayName("Claude 가 오류를 주면 AI_CALL_FAILED")
    void serverErrorBecomesAiCallFailed() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ClaudeMessageClient client = new ClaudeMessageClient(builder.build(), "test-key", "claude-sonnet-5-5");

        server.expect(requestTo(BASE + "/v1/messages")).andRespond(withServerError());

        assertThatThrownBy(() -> client.complete("질문"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_CALL_FAILED));
    }

    @Test
    @DisplayName("키가 없으면 외부 호출 없이 AI_NOT_CONFIGURED")
    void blankKeyDoesNotCallOutside() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ClaudeMessageClient client = new ClaudeMessageClient(builder.build(), "", "claude-sonnet-5-5");

        assertThatThrownBy(() -> client.complete("질문"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_NOT_CONFIGURED));
        server.verify();
    }
}
