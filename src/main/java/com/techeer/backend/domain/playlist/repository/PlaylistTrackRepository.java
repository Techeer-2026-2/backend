package com.techeer.backend.domain.playlist.repository;

import com.techeer.backend.domain.playlist.entity.PlaylistTrack;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaylistTrackRepository extends JpaRepository<PlaylistTrack, Long> {
}
