package com.techeer.backend.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import java.util.List;
import java.util.Collections;
import java.util.UUID;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;

@Testcontainers
class EventPublisherTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    private KafkaTemplate<String, String> template;
    private EventPublisher publisher;
    private String topic;

    @BeforeEach
    void setUp() throws Exception {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                props, new StringSerializer(), new StringSerializer()));
        publisher = new EventPublisher(template);
        topic = "test-" + UUID.randomUUID();
        try (AdminClient admin = AdminClient.create(Map.of(
                "bootstrap.servers", kafka.getBootstrapServers()))) {
            admin.createTopics(Collections.singletonList(new NewTopic(topic, 3, (short) 1))).all().get();
        }
    }

    @AfterEach
    void tearDown() {
        template.destroy();
    }

    @Test
    void 발행하면_브로커에_같은_키와_내용이_들어간다() {
        publisher.publish(topic, "12", "{\"n\":\"A1\"}");

        List<ConsumerRecord<String, String>> records = readAll(1);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).key()).isEqualTo("12");
        assertThat(records.get(0).value()).isEqualTo("{\"n\":\"A1\"}");
    }

    @Test
    void 브로커에_닿지_않으면_예외가_난다() {
        // 주소가 틀린 생산자: 메타데이터를 못 받아 1초 뒤 실패해야 한다 (확인 응답 없음 = 실패)
        KafkaTemplate<String, String> broken = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1",
                        ProducerConfig.MAX_BLOCK_MS_CONFIG, 1000),
                new StringSerializer(), new StringSerializer()));
        try {
            assertThatThrownBy(() -> new EventPublisher(broken).publish(topic, "1", "{}"))
                    .isInstanceOf(EventPublishException.class);
        } finally {
            broken.destroy();
        }
    }

    private List<ConsumerRecord<String, String>> readAll(int expected) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(
                props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topic));
            java.util.ArrayList<ConsumerRecord<String, String>> out = new java.util.ArrayList<>();
            long end = System.currentTimeMillis() + 20_000;
            while (out.size() < expected && System.currentTimeMillis() < end) {
                consumer.poll(Duration.ofMillis(500)).forEach(out::add);
            }
            return out;
        }
    }
}
