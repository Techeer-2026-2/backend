package com.techeer.backend.domain.recompute;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@Testcontainers
class KafkaBatchConsumerIntegrationTest {

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    @Test
    void resumesFromTheLastCommittedOffsetAfterWritingButBeforeCommit() throws Exception {
        String topic = "replay-test";
        String group = "replay-test-group";
        var kafka = new KafkaProperties();
        kafka.setBootstrapServers(List.of(KAFKA.getBootstrapServers()));
        var written = new LinkedBlockingQueue<String>();
        Map<String, Object> props = Map.of("bootstrap.servers", KAFKA.getBootstrapServers());
        try (Admin admin = Admin.create(props);
                var producer = new KafkaProducer<>(props, new StringSerializer(), new StringSerializer())) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get(30, TimeUnit.SECONDS);
            producer.send(new ProducerRecord<>(topic, "1", "click")).get(10, TimeUnit.SECONDS);
            var failed = new KafkaBatchConsumer(kafka, group, List.of(topic), 1, 1000, Duration.ofMillis(200), batch -> {
                written.add(batch.getFirst().value());
                throw new IllegalStateException("Test failure after storage and before commit");
            });
            failed.start();
            try {
                assertThat(written.poll(20, TimeUnit.SECONDS)).isEqualTo("click");
            } finally {
                failed.stop();
            }
            var partition = new TopicPartition(topic, 0);
            assertThat(admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS))
                    .doesNotContainKey(partition);
            written.clear();

            var resumed = new KafkaBatchConsumer(kafka, group, List.of(topic), 1, 1000, Duration.ofMillis(200),
                    batch -> written.add(batch.getFirst().value()));
            resumed.start();
            try {
                assertThat(written.poll(20, TimeUnit.SECONDS)).isEqualTo("click");
            } finally {
                resumed.stop();
            }
            var committed = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);
            assertThat(committed.get(partition).offset()).isEqualTo(1);
        }
    }
}
