package com.techeer.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),

    // 캠페인
    INVALID_CAMPAIGN_PERIOD(HttpStatus.BAD_REQUEST, "노출 종료 시각은 시작 시각보다 뒤여야 합니다."),

    // 타겟 유저
    TARGET_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 유저입니다."),

    // 노출
    IMPRESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 알림(notification_id)입니다.");

    private final HttpStatus status;
    private final String message;
}
