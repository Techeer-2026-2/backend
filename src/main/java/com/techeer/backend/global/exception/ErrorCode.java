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
    CAMPAIGN_PERIOD_ALREADY_ENDED(HttpStatus.BAD_REQUEST, "노출 종료 시각은 현재 시각보다 뒤여야 합니다."),
    CAMPAIGN_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 캠페인입니다."),
    CAMPAIGN_ACCESS_DENIED(HttpStatus.FORBIDDEN, "내 캠페인이 아닙니다."),
    CAMPAIGN_FIELD_NOT_EDITABLE(HttpStatus.CONFLICT, "진행중인 캠페인은 문구와 배너 이미지만 수정할 수 있습니다."),
    CAMPAIGN_ENDED(HttpStatus.CONFLICT, "종료된 캠페인은 수정할 수 없습니다."),

    // 광고주
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    ADVERTISER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 광고주입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 토큰입니다."),

    // 타겟 유저
    TARGET_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 유저입니다."),

    // 노출
    IMPRESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 알림(notification_id)입니다."),

    // 회원 식별
    MISSING_MEMBER_ID(HttpStatus.BAD_REQUEST, "X-Member-Id 헤더가 필요합니다."),
    INVALID_MEMBER_ID(HttpStatus.BAD_REQUEST, "X-Member-Id 헤더 값이 올바르지 않습니다."),

    // 플레이리스트
    PLAYLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 플레이리스트입니다."),
    PLAYLIST_TRACK_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 트랙입니다."),
    // 외부 API
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 API 호출에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}
