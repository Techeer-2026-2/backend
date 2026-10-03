package com.techeer.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),

    // 타겟 유저
    TARGET_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 유저입니다."),

    // 노출
    IMPRESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 알림(notification_id)입니다."),

    TRACK_NOT_FOUND(HttpStatus.NOT_FOUND, "등록된 곡을 찾을 수 없습니다."),
    PLAYBACK_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "재생 시작 세션을 찾을 수 없습니다."),
    PLAYBACK_CONFLICT(HttpStatus.CONFLICT, "이미 사용된 이벤트 또는 세션 ID의 내용이 다릅니다.");

    private final HttpStatus status;
    private final String message;
}
