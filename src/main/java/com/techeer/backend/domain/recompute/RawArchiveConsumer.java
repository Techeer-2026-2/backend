package com.techeer.backend.domain.recompute;

import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;

final class RawArchiveConsumer {

    private final RawBatchBuffer buffer;
    private final S3RawWriter writer;
    private final PipelineLogRepository logRepository;

    RawArchiveConsumer(RawBatchBuffer buffer, S3RawWriter writer, PipelineLogRepository logRepository) {
        this.buffer = buffer;
        this.writer = writer;
        this.logRepository = logRepository;
    }

    void archive(List<ConsumerRecord<String, String>> records) {
        for (RawBatchBuffer.ArchiveFile file : buffer.close(records)) {
            writer.upload(file);
            logRepository.recordArchive(file);
        }
    }
}
