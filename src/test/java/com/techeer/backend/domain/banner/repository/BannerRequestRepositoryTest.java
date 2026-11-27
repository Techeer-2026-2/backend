package com.techeer.backend.domain.banner.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.banner.entity.BannerRequest;
import com.techeer.backend.global.config.ClockConfig;
import com.techeer.backend.global.config.JpaConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** 같은 요청 ID 는 두 번째부터 0 을 돌려주고 행은 하나만 남아야 한다 (실제 PostgreSQL). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JpaConfig.class, ClockConfig.class})
class BannerRequestRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 26, 12, 0);

    @Autowired
    private BannerRequestRepository bannerRequestRepository;

    @Test
    void 같은_요청_ID_두_번째는_0을_돌려주고_행은_하나다() {
        assertThat(bannerRequestRepository.insertIfAbsent("req-1", 7L, NOW)).isEqualTo(1);
        assertThat(bannerRequestRepository.insertIfAbsent("req-1", 7L, NOW)).isEqualTo(0);

        assertThat(bannerRequestRepository.count()).isEqualTo(1);
    }

    @Test
    void 노출_ID_는_나중에_채울_수_있고_후보가_없으면_null_이다() {
        bannerRequestRepository.insertIfAbsent("req-2", 7L, NOW);
        bannerRequestRepository.insertIfAbsent("req-3", 8L, NOW);

        BannerRequest withImpression = bannerRequestRepository.findById("req-2").orElseThrow();
        withImpression.assignNotification("n-100");
        bannerRequestRepository.flush();

        assertThat(bannerRequestRepository.findById("req-2").orElseThrow().getNotificationId()).isEqualTo("n-100");
        assertThat(bannerRequestRepository.findById("req-3").orElseThrow().getNotificationId()).isNull();
    }
}
