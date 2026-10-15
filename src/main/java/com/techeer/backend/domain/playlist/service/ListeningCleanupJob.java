package com.techeer.backend.domain.playlist.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ListeningCleanupJob {
    private final PlaylistService playlists;

    @Scheduled(fixedDelayString = "${playlist.listening.cleanup-delay-ms:60000}")
    public void clean() {
        playlists.cleanupExpired();
    }
}
