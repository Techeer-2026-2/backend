package com.techeer.backend.global.exception;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "에러 응답")
public record ErrorResponse(
        @Schema(description = "에러 코드", example = "IMPRESSION_NOT_FOUND") String code,
        @Schema(description = "에러 메시지", example = "존재하지 않는 알림(notification_id)입니다.") String message) {

    /**
     * 오류 코드의 enum 이름과 기본 메시지로 에러 응답을 생성한다.
     *
     * @param errorCode 응답에 사용할 오류 코드
     * @return 기본 메시지를 담은 에러 응답
     */
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage());
    }

    /**
     * 오류 코드의 enum 이름과 지정한 메시지로 에러 응답을 생성한다.
     *
     * @param errorCode 응답에 사용할 오류 코드
     * @param message 기본 메시지 대신 전달할 상세 메시지
     * @return 지정한 메시지를 담은 에러 응답
     */
    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message);
    }
}
