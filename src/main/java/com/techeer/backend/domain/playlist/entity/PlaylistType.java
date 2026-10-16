package com.techeer.backend.domain.playlist.entity;

public enum PlaylistType {
    /** 사용자가 직접 만든 플레이리스트 */
    USER_CREATED,
    /** 서버가 날씨·시간대·통근 정보로 자동 생성한 플레이리스트 */
    COMMUTE_GENERATED
}
