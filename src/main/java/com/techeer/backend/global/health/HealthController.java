package com.techeer.backend.global.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CI 스모크 테스트와 배포 검증이 때리는 엔드포인트.
 *
 * <p>actuator 의 /actuator/health 는 NGINX 에서 외부 차단하므로, 외부에서 확인 가능한 헬스 체크를 따로 둔다.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Health", description = "서버 상태 확인")
public class HealthController {

    @Operation(summary = "헬스 체크", description = "서버가 요청을 처리할 수 있는 상태인지 확인한다.")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "commute-playlist-backend",
                "timestamp", OffsetDateTime.now().toString()));
    }
}
