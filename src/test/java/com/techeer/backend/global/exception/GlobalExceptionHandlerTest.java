package com.techeer.backend.global.exception;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예외 → 에러 JSON 변환 테스트. 스프링 컨텍스트·DB 없이 도는 빠른 테스트.
 * 테스트 안에서 일부러 예외를 던지는 가짜 컨트롤러를 만들어 쓴다.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FakeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("BusinessException 은 ErrorCode 의 상태코드와 에러 JSON 으로 변환된다")
    void businessExceptionIsConverted() throws Exception {
        mockMvc.perform(get("/fake/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TARGET_USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 유저입니다."));
    }

    @Test
    @DisplayName("입력 검증 실패는 400 과 '필드명: 사유' 메시지를 돌려준다")
    void validationFailureReturnsFieldMessage() throws Exception {
        mockMvc.perform(post("/fake/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value(startsWith("name:")));
    }

    @RestController
    static class FakeController {

        @GetMapping("/fake/business")
        String business() {
            throw new BusinessException(ErrorCode.TARGET_USER_NOT_FOUND);
        }

        @PostMapping("/fake/validate")
        String validate(@Valid @RequestBody FakeRequest request) {
            return request.name();
        }
    }

    record FakeRequest(@NotBlank String name) {
    }
}
