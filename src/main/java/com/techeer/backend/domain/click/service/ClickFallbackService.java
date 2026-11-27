package com.techeer.backend.domain.click.service;

import com.techeer.backend.domain.click.entity.ClickFallback;
import com.techeer.backend.domain.click.repository.ClickFallbackRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Kafka 발행이 실패한 클릭을 DB 에 보관한다. 이 표는 Kafka 가 죽었을 때만 쓰인다. */
@Service
@RequiredArgsConstructor
public class ClickFallbackService {

    private final ClickFallbackRepository clickFallbackRepository;
    private final Clock clock;

    @Transactional
    public void save(String notificationId, String payload) {
        clickFallbackRepository.save(ClickFallback.create(notificationId, payload, LocalDateTime.now(clock)));
    }
}
