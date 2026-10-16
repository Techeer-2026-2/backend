package com.techeer.backend.domain.playlist.entity;

/** 분위기 점수가 어디서 온 값인지. */
public enum TrackMoodSource {
    /** 수집할 때 어떤 검색어로 찾았는지만 반영한 원시 값 (점수는 항상 1.0) */
    KEYWORD,
    /** 검색어·수치 데이터·모델 답을 합산해 계산한 최종 값 */
    SCORED
}
