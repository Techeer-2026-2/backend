package com.techeer.backend.domain.playlist.repository;

import com.techeer.backend.domain.playlist.entity.MusicPlaylist;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MusicPlaylistRepository extends JpaRepository<MusicPlaylist, Long> {
    List<MusicPlaylist> findByOwnerUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long ownerUserId);

    Optional<MusicPlaylist> findByPlaylistIdAndDeletedAtIsNull(Long playlistId);

    Optional<MusicPlaylist> findByShareCodeAndDeletedAtIsNull(String shareCode);
}
