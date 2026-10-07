package com.techeer.backend.domain.advertiser.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.advertiser.entity.Advertiser;
import com.techeer.backend.global.config.ClockConfig;
import com.techeer.backend.global.config.JpaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * AdvertiserRepository 를 실제 PostgreSQL(Testcontainers)에 붙여 검증한다. 실행에는 Docker 가 필요하다.
 *
 * <p>@DataJpaTest 는 각 테스트를 트랜잭션으로 감싸고 끝나면 롤백하므로 테스트끼리 데이터가 섞이지 않는다.
 * JPA 슬라이스 테스트는 @Configuration 을 자동으로 읽지 않으므로, created_at 자동 기록에 필요한
 * JpaConfig·ClockConfig 를 직접 불러온다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JpaConfig.class, ClockConfig.class})
class AdvertiserRepositoryTest {

    @Autowired
    private AdvertiserRepository advertiserRepository;

    @Test
    @DisplayName("저장하면 id 가 생기고 plan 은 free, 생성 시각이 채워진다")
    void saveAssignsIdAndDefaults() {
        Advertiser saved = advertiserRepository.saveAndFlush(Advertiser.create("a@test.com", "hash", "테커 카페"));

        assertThat(saved.getUserId()).isNotNull();
        assertThat(saved.getPlan()).isEqualTo("free");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("이메일로 존재 여부와 조회가 된다")
    void findByEmail() {
        advertiserRepository.saveAndFlush(Advertiser.create("a@test.com", "hash", "테커 카페"));

        assertThat(advertiserRepository.existsByEmail("a@test.com")).isTrue();
        assertThat(advertiserRepository.existsByEmail("none@test.com")).isFalse();
        assertThat(advertiserRepository.findByEmail("a@test.com"))
                .get()
                .extracting(Advertiser::getBusinessName)
                .isEqualTo("테커 카페");
    }

    @Test
    @DisplayName("같은 이메일을 두 번 저장하면 DB unique 제약 위반이 난다")
    void duplicateEmailViolatesUnique() {
        advertiserRepository.saveAndFlush(Advertiser.create("a@test.com", "hash", "테커 카페"));

        assertThatThrownBy(() -> advertiserRepository.saveAndFlush(Advertiser.create("a@test.com", "hash2", "다른 가게")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
