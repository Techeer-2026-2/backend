package com.techeer.backend.domain.recompute;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class RawBatchBufferTest {

    @Test
    void splitsFilesByTopicAndEventHourAndKeepsDuplicateLines() {
        var buffer = new RawBatchBuffer(new ObjectMapper());
        var files = buffer.close(List.of(
                record("ad-clicked", 0, "2026-11-22 23:20:00"),
                record("ad-clicked", 1, "2026-11-22 23:20:00"),
                record("ad-clicked", 2, "2026-11-23 00:00:00"),
                record("ad-clicked", 3, "2026-11-23 01:00:00"),
                record("ad-impressed", 0, "2026-11-23 01:00:00")));

        assertThat(files).hasSize(4);
        assertThat(files.get(0).key()).startsWith("raw-clicks/2026/11/22/23/").endsWith(".json");
        assertThat(files.get(0).lines()).hasSize(2).allSatisfy(line -> assertThat(line).doesNotContain("\n"));
        assertThat(files.get(0).lines().get(0)).isEqualTo(files.get(0).lines().get(1));
        assertThat(files.get(1).key()).startsWith("raw-clicks/2026/11/23/00/");
        assertThat(files.get(2).key()).startsWith("raw-clicks/2026/11/23/01/");
        assertThat(files.get(3).key()).startsWith("raw-impressions/2026/11/23/01/");
    }

    private ConsumerRecord<String, String> record(String topic, long offset, String time) {
        return new ConsumerRecord<>(topic, 0, offset, "1", """
                {
                  "notification_id":"test-id",
                  "event_at":"%s"
                }
                """.formatted(time));
    }
}
