package com.techeer.backend.domain.playlist.entity;

/** 곡의 분위기 태그. iTunes 장르(Pop, Jazz …)보다 좁게, 날씨·시간대별 추천에 쓰려고 우리가 붙인다. */
public enum TrackMood {
    /** 우울하고 감성적인 곡 */
    GLOOMY,
    /** 잔잔하고 차분한 곡 */
    CALM,
    /** 밝고 경쾌한 곡 */
    UPBEAT,
    /** 에너지가 넘치는 곡 */
    ENERGETIC,
    /** 집중·배경음악 */
    FOCUS,
    /** 설렘·로맨틱 */
    ROMANTIC,
    /** 추억·복고 */
    NOSTALGIC,
    /** 몽환·새벽 감성 */
    DREAMY
}
