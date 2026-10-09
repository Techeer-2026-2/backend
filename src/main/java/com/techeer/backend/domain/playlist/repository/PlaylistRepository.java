package com.techeer.backend.domain.playlist.repository;

import com.techeer.backend.domain.playlist.entity.Playlist;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {
    List<Playlist> findAllByMemberId(Long memberId);
}
