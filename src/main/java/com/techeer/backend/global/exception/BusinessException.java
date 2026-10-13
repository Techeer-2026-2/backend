package com.techeer.backend.global.exception;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반. GlobalExceptionHandler 가 ErrorCode 의 상태 코드로 응답한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 오류 코드와 해당 코드의 기본 메시지로 비즈니스 예외를 생성한다.
     *
     * @param errorCode 응답 상태와 기본 메시지를 정의한 오류 코드
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 원인 예외를 예외 체인에 남기는 생성자. 응답에는 원인을 싣지 않고 서버 로그에서만 확인한다.
     *
     * @param errorCode 응답 상태와 기본 메시지를 정의한 오류 코드
     * @param cause 원래 발생한 예외
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
