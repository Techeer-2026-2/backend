package com.techeer.backend.domain.playlist.repository;

import com.techeer.backend.domain.playlist.entity.Track;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackRepository extends JpaRepository<Track, Long> {

    Optional<Track> findByItunesTrackId(Long itunesTrackId);
}
