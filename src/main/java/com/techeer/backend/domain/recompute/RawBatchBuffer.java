package com.techeer.backend.domain.recompute;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;

final class RawBatchBuffer {

    private static final DateTimeFormatter HOUR_PATH = DateTimeFormatter.ofPattern("uuuu/MM/dd/HH", Locale.ROOT);
    private static final DateTimeFormatter DAY_PATH = DateTimeFormatter.ofPattern("uuuu/MM/dd", Locale.ROOT);

    private final ObjectMapper mapper;

    RawBatchBuffer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    List<ArchiveFile> close(List<ConsumerRecord<String, String>> records) {
        var files = new LinkedHashMap<Bucket, List<String>>();
        for (ConsumerRecord<String, String> record : records) {
            AdEvent event = AdEvent.parse(mapper, record.value());
            var bucket = new Bucket(record.topic(), event.eventAt().truncatedTo(ChronoUnit.HOURS));
            files.computeIfAbsent(bucket, key -> new ArrayList<>()).add(event.payload().toString());
        }
        return files.entrySet().stream()
                .map(entry -> new ArchiveFile(UUID.randomUUID(), entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    static String dayGlob(String topic, LocalDate date) {
        return prefix(topic) + "/" + DAY_PATH.format(date) + "/*/*.json";
    }

    private static String prefix(String topic) {
        return switch (topic) {
            case AdEvent.CLICKED -> "raw-clicks";
            case AdEvent.IMPRESSED -> "raw-impressions";
            default -> throw new IllegalArgumentException("Unexpected ad event topic: " + topic);
        };
    }

    record Bucket(String topic, LocalDateTime hour) {
    }

    record ArchiveFile(UUID id, Bucket bucket, List<String> lines) {

        String key() {
            return prefix(bucket.topic()) + "/" + HOUR_PATH.format(bucket.hour()) + "/" + id + ".json";
        }
    }
}
