package com.techeer.backend.domain.recompute;

import java.net.URI;
import java.time.Duration;
import org.springframework.util.Assert;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

final class S3RawWriter implements AutoCloseable {

    private final S3Client client;
    private final String bucket;

    S3RawWriter(String bucket, String region, String endpoint) {
        Assert.hasText(bucket, "raw.s3.bucket is required for archiving");
        this.bucket = bucket;
        var builder = S3Client.builder().region(Region.of(region))
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(30)));
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
        }
        this.client = builder.build();
    }

    S3RawWriter(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    void upload(RawBatchBuffer.ArchiveFile file) {
        var request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(file.key())
                .contentType("application/x-ndjson")
                .build();
        client.putObject(request, RequestBody.fromString(String.join("\n", file.lines()) + "\n"));
    }

    @Override
    public void close() {
        client.close();
    }
}
