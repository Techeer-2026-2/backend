package com.techeer.backend.domain.advertiser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.advertiser.dto.SignupRequest;
import com.techeer.backend.domain.advertiser.entity.Advertiser;
import com.techeer.backend.domain.advertiser.entity.Plan;
import com.techeer.backend.domain.advertiser.repository.AdvertiserRepository;
import com.techeer.backend.domain.advertiser.service.AdvertiserService;
import com.techeer.backend.global.exception.BusinessException;
import com.techeer.backend.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * POST /api/v1/owners/signup 을 실제 PostgreSQL(Testcontainers)에 붙여 검증한다.
 *
 * <p>중복 가입은 UNIQUE 제약에 기대므로 H2 가 아닌 실제 DB 로 테스트해야 의미가 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AdvertiserSignupApiIntegrationTest {

    private static final String URL = "/api/v1/owners/signup";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdvertiserService advertiserService;

    @Autowired
    private AdvertiserRepository advertiserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 각 테스트가 가입시킨 광고주를 삭제해 다음 테스트를 격리한다.
     */
    @AfterEach
    void tearDown() {
        advertiserRepository.deleteAll();
    }

    /**
     * 가입하면 201 이 오고, 응답에는 비밀번호 관련 값이 없으며, DB 에는 평문이 아닌 bcrypt 해시가 저장되는지 검증한다.
     */
    @Test
    @DisplayName("가입하면 201, 응답에 비밀번호가 없고 DB 에는 bcrypt 해시가 저장된다")
    void signupSucceeds() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("owner@test.com", "password1234", "테커 카페")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.email").value("owner@test.com"))
                .andExpect(jsonPath("$.businessName").value("테커 카페"))
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        Advertiser saved = advertiserRepository.findByEmail("owner@test.com").orElseThrow();
        assertThat(saved.getPlan()).isEqualTo(Plan.FREE);
        assertThat(saved.getPasswordHash()).isNotEqualTo("password1234");
        assertThat(saved.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("password1234", saved.getPasswordHash())).isTrue();
    }

    /**
     * 같은 이메일(대소문자만 다른 경우 포함)로 다시 가입하면 409 이고 광고주가 늘어나지 않는지 검증한다.
     */
    @Test
    @DisplayName("이미 가입된 이메일은 대소문자가 달라도 409 EMAIL_DUPLICATED")
    void duplicateEmailIsRejected() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("owner@test.com", "password1234", "테커 카페")))
                .andExpect(status().isCreated());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("OWNER@Test.com", "otherpassword", "다른 가게")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_DUPLICATED"));

        assertThat(advertiserRepository.count()).isEqualTo(1);
    }

    /**
     * 형식이 틀린 입력은 400 INVALID_INPUT 이고 아무것도 저장되지 않는지 검증한다.
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({
            "이메일 형식 오류,      not-an-email,   password1234, 테커 카페",
            "비밀번호 8자 미만,     owner@test.com, short12,      테커 카페",
            "사업자명 공백,         owner@test.com, password1234, '   '"
    })
    @DisplayName("입력 검증에 실패하면 400 INVALID_INPUT 이고 저장되지 않는다")
    void invalidInputIsRejected(String caseName, String email, String password, String businessName)
            throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, password, businessName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(advertiserRepository.count()).isZero();
    }

    /**
     * bcrypt 한계(72바이트)를 글자 수가 아니라 바이트로 검증하는지 확인한다.
     * 영문 72자와 한글 24자(72바이트)는 가입되고, 영문 73자와 한글 25자(75바이트)는 500 이 아니라 400 이어야 한다.
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({
            "영문 72자,        a, 72, 201",
            "영문 73자,        a, 73, 400",
            "한글 24자(72B),   가, 24, 201",
            "한글 25자(75B),   가, 25, 400"
    })
    @DisplayName("비밀번호는 글자 수가 아니라 UTF-8 72바이트까지만 허용한다")
    void passwordIsLimitedByBytes(String caseName, String unit, int repeat, int expectedStatus) throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("bytes@test.com", unit.repeat(repeat), "테커 카페")))
                .andExpect(status().is(expectedStatus));
    }

    /**
     * 같은 이메일로 동시에 가입을 시도해도 정확히 한 명만 가입되고 나머지는 EMAIL_DUPLICATED 로 거절되는지 검증한다.
     *
     * <p>두 요청이 existsByEmail 검사를 모두 통과하는 타이밍이면 DB unique 제약이, 아니면 검사가 막는다.
     * 어느 쪽이든 결과가 같아야 한다.
     */
    @Test
    @DisplayName("같은 이메일로 동시에 가입해도 한 명만 가입되고 나머지는 EMAIL_DUPLICATED")
    void concurrentSignupCreatesOnlyOne() throws Exception {
        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        SignupRequest request = new SignupRequest("race@test.com", "password1234", "경합 카페");

        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Boolean> task = () -> {
                start.await();
                try {
                    advertiserService.signup(request);
                    return true;
                } catch (BusinessException e) {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EMAIL_DUPLICATED);
                    return false;
                }
            };
            results.add(executor.submit(task));
        }
        start.countDown();

        int succeeded = 0;
        for (Future<Boolean> result : results) {
            if (result.get(30, TimeUnit.SECONDS)) {
                succeeded++;
            }
        }
        executor.shutdown();

        assertThat(succeeded).isEqualTo(1);
        assertThat(advertiserRepository.count()).isEqualTo(1);
    }

    private String body(String email, String password, String businessName) {
        return """
                {"email": "%s", "password": "%s", "businessName": "%s"}
                """.formatted(email, password, businessName);
    }
}
