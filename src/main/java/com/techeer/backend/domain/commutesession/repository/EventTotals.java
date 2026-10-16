package com.techeer.backend.domain.commutesession.repository;

import com.techeer.backend.domain.commutesession.entity.PlaybackAction;

/** 한 곡의 한 행동에 대한 이벤트 개수와 들은 시간(초) 합계. */
public record EventTotals(Long itunesTrackId, PlaybackAction action, long count, long playedSeconds) {
}
