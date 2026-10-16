package com.techeer.backend.domain.playlist.repository;

import com.techeer.backend.domain.playlist.entity.Playlist;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {
    List<Playlist> findAllByMemberId(Long memberId);

    /** 공유 토큰을 만들 때 같은 플레이리스트에 동시에 요청이 와도 토큰이 하나만 만들어지도록 행을 잠그고 읽는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Playlist p where p.id = :id")
    Optional<Playlist> findByIdForUpdate(@Param("id") Long id);

    /** 공유 중인 플레이리스트를 토큰으로 찾는다. 공유를 취소했거나 없는 토큰이면 비어 있다. */
    Optional<Playlist> findByShareToken(String shareToken);
}
