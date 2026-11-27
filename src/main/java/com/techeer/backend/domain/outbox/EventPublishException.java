package com.techeer.backend.domain.outbox;

/** Kafka 발행이 확인 응답을 받지 못했을 때 던진다. 호출한 쪽이 실패 처리(202 보관 또는 503)를 정한다. */
public class EventPublishException extends RuntimeException {

    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
