package com.techeer.backend.domain.commuteprofile;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.backend.TestcontainersConfiguration;
import com.techeer.backend.domain.commuteprofile.entity.CommuteProfile;
import com.techeer.backend.domain.commuteprofile.entity.CommuteRoute;
import com.techeer.backend.domain.commuteprofile.entity.CommuteType;
import com.techeer.backend.domain.commuteprofile.entity.Location;
import com.techeer.backend.domain.commuteprofile.repository.CommuteProfileRepository;
import com.techeer.backend.domain.commuteprofile.repository.CommuteRouteRepository;
import com.techeer.backend.domain.tmap.TransportMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 통근 경로(commute_routes)가 실제 PostgreSQL 에 저장되는지 확인한다.
 *
 * <p>{@code route_detail} 은 jsonb 칼럼이다. 엔티티가 이 값을 문자열(varchar)로 바인딩하면 PostgreSQL 이 저장을 거절하므로
 * H2 가 아니라 실제 DB 로 확인해야 의미가 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CommuteRoutePersistenceTest {

    @Autowired
    private CommuteProfileRepository profileRepository;

    @Autowired
    private CommuteRouteRepository routeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    @AfterEach
    void clean() {
        routeRepository.deleteAll();
        profileRepository.deleteAll();
    }

    private CommuteProfile profile() {
        return profileRepository.save(CommuteProfile.builder()
                .memberId(1L).commuteType(CommuteType.TO_WORK).frequentRouteName("강남-판교")
                .departure(new Location(37.5, 127.0, "강남")).arrival(new Location(37.4, 127.1, "판교")).build());
    }

    @Test
    @DisplayName("경로 상세가 없어도(null) 통근 경로가 저장된다")
    void saveRouteWithoutDetail() {
        CommuteRoute saved = routeRepository.saveAndFlush(CommuteRoute.builder().commuteProfile(profile())
                .transportMode(TransportMode.PUBLIC_TRANSIT).averageDurationMinutes(32).build());

        CommuteRoute found = routeRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getAverageDurationMinutes()).isEqualTo(32);
        assertThat(found.getRouteDetail()).isNull();
        assertThat(jdbcTemplate.queryForObject("select route_detail is null from commute_routes", Boolean.class))
                .isTrue();
    }

    @Test
    @DisplayName("JSON 문자열 경로 상세는 jsonb 객체로 저장되고 같은 내용으로 읽힌다")
    void saveRouteWithJsonDetail() throws Exception {
        String json = "{\"legs\":[{\"mode\":\"WALK\",\"minutes\":5},{\"mode\":\"SUBWAY\",\"minutes\":27}]}";
        CommuteRoute saved = routeRepository.saveAndFlush(CommuteRoute.builder().commuteProfile(profile())
                .transportMode(TransportMode.PUBLIC_TRANSIT).averageDurationMinutes(32).routeDetail(json).build());

        // 문자열 하나로 저장된 것이 아니라 JSON 객체로 저장됐는지(jsonb_typeof) 확인한다
        assertThat(jdbcTemplate.queryForObject("select jsonb_typeof(route_detail) from commute_routes", String.class))
                .isEqualTo("object");
        CommuteRoute found = routeRepository.findById(saved.getId()).orElseThrow();
        assertThat(objectMapper.readTree(found.getRouteDetail())).isEqualTo(objectMapper.readTree(json));
    }

    @Test
    @DisplayName("저장된 경로의 소요시간을 수정해도 경로 상세는 그대로 유지된다")
    void updateKeepsDetail() throws Exception {
        String json = "{\"a\":1}";
        CommuteRoute saved = routeRepository.saveAndFlush(CommuteRoute.builder().commuteProfile(profile())
                .transportMode(TransportMode.CAR).averageDurationMinutes(20).routeDetail(json).build());

        CommuteRoute route = routeRepository.findById(saved.getId()).orElseThrow();
        route.update(TransportMode.WALK, 45, route.getRouteDetail());
        routeRepository.saveAndFlush(route);

        CommuteRoute found = routeRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getTransportMode()).isEqualTo(TransportMode.WALK);
        assertThat(found.getAverageDurationMinutes()).isEqualTo(45);
        assertThat(objectMapper.readTree(found.getRouteDetail())).isEqualTo(objectMapper.readTree(json));
    }
}
