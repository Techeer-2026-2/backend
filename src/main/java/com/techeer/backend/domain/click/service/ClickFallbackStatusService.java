package com.techeer.backend.domain.click.service;

import com.techeer.backend.domain.click.repository.ClickFallbackRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 보관된 클릭을 재전송 완료(SENT)로 바꾸는 짧은 트랜잭션. 행마다 따로 호출한다. */
@Service
@RequiredArgsConstructor
public class ClickFallbackStatusService {

    private final ClickFallbackRepository clickFallbackRepository;
    private final Clock clock;

    @Transactional
    public void markSent(Long fallbackId) {
        clickFallbackRepository.findById(fallbackId).orElseThrow().markSent(LocalDateTime.now(clock));
    }
}
