package com.techeer.backend.global.exception;

import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 비즈니스 예외를 오류 코드에 정의된 HTTP 상태와 기본 메시지로 변환한다.
     *
     * @param e 발생한 비즈니스 예외
     * @return 오류 코드의 상태와 에러 응답
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode));
    }

    /**
     * 요청 검증에 실패한 필드 이름과 메시지를 쉼표로 연결해 400 응답을 만든다.
     *
     * @param e 요청 필드 검증 예외
     * @return INVALID_INPUT 코드와 필드별 오류 메시지를 담은 응답
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return invalidInput(message);
    }

    /**
     * 읽을 수 없는 요청 본문을 공통 안내 메시지가 포함된 400 응답으로 변환한다.
     *
     * @param e 요청 본문을 읽는 중 발생한 예외
     * @return INVALID_INPUT 코드와 JSON 본문 오류 안내를 담은 응답
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return invalidInput("요청 본문(JSON)을 읽을 수 없습니다.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        return invalidInput(e.getParameterName() + ": 필수 값입니다.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return invalidInput(e.getName() + ": 형식이 올바르지 않습니다.");
    }

    /**
     * 컨트롤러 파라미터(@RequestParam 등)에 직접 붙인 검증 어노테이션이 실패하면 400 으로 변환한다.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
        return invalidInput("요청 값이 올바르지 않습니다.");
    }

    private ResponseEntity<ErrorResponse> invalidInput(String message) {
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, message));
    }
}
