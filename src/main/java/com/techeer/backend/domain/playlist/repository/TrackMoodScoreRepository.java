package com.techeer.backend.domain.playlist.repository;

import com.techeer.backend.domain.playlist.entity.TrackMood;
import com.techeer.backend.domain.playlist.entity.TrackMoodScore;
import com.techeer.backend.domain.playlist.entity.TrackMoodSource;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrackMoodScoreRepository extends JpaRepository<TrackMoodScore, Long> {

    Optional<TrackMoodScore> findByTrackIdAndMood(Long trackId, TrackMood mood);

    List<TrackMoodScore> findAllByTrackId(Long trackId);

    /** 추천 후보: 지정한 분위기 중 하나 이상에 minScore 이상으로 걸린 곡의 점수 행들(곡 정보 포함). */
    @Query("select s from TrackMoodScore s join fetch s.track t "
            + "where s.mood in :moods and s.score >= :minScore and s.deletedAt is null and t.deletedAt is null")
    List<TrackMoodScore> findCandidates(@Param("moods") Collection<TrackMood> moods,
            @Param("minScore") double minScore);

    /** 곡 정보까지 한 번에 가져온다(수집 결과 파일을 쓸 때 곡마다 다시 조회하지 않으려고). */
    @Query("select s from TrackMoodScore s join fetch s.track where s.source = :source order by s.track.id, s.mood")
    List<TrackMoodScore> findAllWithTrackBySource(@Param("source") TrackMoodSource source);
}
