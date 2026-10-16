package com.techeer.backend.domain.playlist.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 재생 신호가 끊겨 만료된 청취 기록을 주기적으로 지운다. 스케줄링은 RankingConfig 의 @EnableScheduling 을 쓴다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "playlist.listening.cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class ListeningCleanupJob {

    private final PlaylistShareService shareService;

    @Scheduled(fixedDelayString = "${playlist.listening.cleanup-delay-ms:60000}")
    public void clean() {
        shareService.cleanupExpired();
    }
}
