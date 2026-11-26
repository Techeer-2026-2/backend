package com.techeer.backend.domain.recompute;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.SmartLifecycle;

final class KafkaBatchConsumer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(KafkaBatchConsumer.class);
    private static final Duration POLL_TIMEOUT = Duration.ofMillis(200);

    private final String group;
    private final List<String> topics;
    private final int concurrency;
    private final int batchSize;
    private final long batchNanos;
    private final Consumer<List<ConsumerRecord<String, String>>> writeBatch;
    private final Map<String, Object> properties;
    private final List<Thread> workers = new ArrayList<>();
    private volatile boolean running;

    KafkaBatchConsumer(KafkaProperties kafka, String group, List<String> topics, int concurrency,
            int batchSize, Duration batchAge, Consumer<List<ConsumerRecord<String, String>>> writeBatch) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Kafka consumer batch size must be positive");
        }
        this.group = group;
        this.topics = topics;
        this.concurrency = concurrency;
        this.batchSize = batchSize;
        this.batchNanos = batchAge.toNanos();
        this.writeBatch = writeBatch;
        properties = new HashMap<>(kafka.buildConsumerProperties(null));
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        for (int i = 0; i < concurrency; i++) {
            Thread worker = new Thread(this::consume, group + "-" + i);
            workers.add(worker);
            worker.start();
        }
    }

    private void consume() {
        while (running) {
            try (var kafka = new KafkaConsumer<String, String>(properties)) {
                var batch = new PendingBatch(kafka);
                kafka.subscribe(topics, batch);
                try {
                    while (running) {
                        for (ConsumerRecord<String, String> record : kafka.poll(POLL_TIMEOUT)) {
                            batch.add(record);
                        }
                        batch.flushIfDue();
                    }
                    batch.flush();
                } finally {
                    // close() may invoke a rebalance callback after a failed write. Replay from Kafka instead.
                    batch.closing = true;
                }
            } catch (RuntimeException e) {
                log.warn("Kafka consumer group {} failed. Uncommitted offsets will be replayed", group, e);
                if (running) {
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        boolean interrupted = false;
        for (Thread worker : workers) {
            while (worker.isAlive()) {
                try {
                    worker.join();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        }
        workers.clear();
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private final class PendingBatch implements ConsumerRebalanceListener {

        private final KafkaConsumer<String, String> kafka;
        private final List<ConsumerRecord<String, String>> records = new ArrayList<>();
        private final Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
        private long openedAt;
        private boolean closing;

        private PendingBatch(KafkaConsumer<String, String> kafka) {
            this.kafka = kafka;
        }

        private void add(ConsumerRecord<String, String> record) {
            if (records.isEmpty()) {
                openedAt = System.nanoTime();
            }
            records.add(record);
            offsets.put(new TopicPartition(record.topic(), record.partition()), new OffsetAndMetadata(record.offset() + 1));
            if (records.size() >= batchSize) {
                flush();
            }
        }

        private void flushIfDue() {
            if (!records.isEmpty() && System.nanoTime() - openedAt >= batchNanos) {
                flush();
            }
        }

        private void flush() {
            if (records.isEmpty()) {
                return;
            }
            writeBatch.accept(records);
            kafka.commitSync(offsets);
            records.clear();
            offsets.clear();
        }

        @Override
        public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            if (!closing) {
                flush();
            }
        }

        @Override
        public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        }

        @Override
        public void onPartitionsLost(Collection<TopicPartition> partitions) {
            if (!closing && !records.isEmpty()) {
                // Reopen the consumer so partitions it retained also resume from their committed offsets.
                throw new IllegalStateException("Kafka partition ownership lost with pending records");
            }
        }
    }
}
