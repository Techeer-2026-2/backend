package com.techeer.backend.domain.outbox;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka 발행 공통 클래스. send() 는 비동기이므로 .get() 으로 브로커의 확인 응답을 기다린다.
 * 확인을 받은 뒤에만 정상으로 본다. 키는 campaign_id 를 문자열로 넣어 같은 캠페인이 같은 파티션으로 간다.
 */
@Component
public class EventPublisher {

    public static final String AD_IMPRESSED = "ad-impressed";
    public static final String AD_CLICKED = "ad-clicked";

    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, String> kafkaTemplate;

    public EventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(String topic, String key, String json) {
        try {
            kafkaTemplate.send(topic, key, json).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishException("Kafka 발행 중단: " + topic, e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            // send() 자체의 동기 예외(브로커에 닿지 않음 등)와 확인 응답 실패를 모두 발행 실패로 본다
            throw new EventPublishException("Kafka 발행 실패: " + topic, e);
        }
    }
}
