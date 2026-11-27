package com.techeer.backend.domain.recompute;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class RecomputeConfiguration {

    @Bean
    PipelineLogRepository pipelineLogRepository(JdbcTemplate jdbc, DataSource dataSource, Clock clock) {
        return new PipelineLogRepository(jdbc, dataSource, clock);
    }

    @Bean
    @Profile("!recompute-command")
    @ConditionalOnProperty(name = "raw.enabled", havingValue = "true")
    S3RawWriter s3RawWriter(@Value("${raw.s3.bucket:}") String bucket,
            @Value("${raw.s3.region:ap-northeast-2}") String region, @Value("${raw.s3.endpoint:}") String endpoint) {
        return new S3RawWriter(bucket, region, endpoint);
    }

    @Bean
    @Profile("!recompute-command")
    @ConditionalOnProperty(name = "raw.enabled", havingValue = "true")
    KafkaBatchConsumer rawArchiveConsumer(KafkaProperties kafka, ObjectMapper mapper, S3RawWriter writer,
            PipelineLogRepository logs) {
        var consumer = new RawArchiveConsumer(new RawBatchBuffer(mapper), writer, logs);
        return new KafkaBatchConsumer(kafka, "raw-archive", List.of(AdEvent.CLICKED, AdEvent.IMPRESSED), 3, 1_000,
                Duration.ofMinutes(1), consumer::archive);
    }

    @Bean
    @Profile("!recompute-command")
    @ConditionalOnProperty(name = "click.copy.enabled", havingValue = "true")
    KafkaBatchConsumer clickCopyConsumer(KafkaProperties kafka, ObjectMapper mapper, JdbcTemplate jdbc,
            TransactionTemplate transaction, @Value("${click.copy.batch-size:500}") int batchSize) {
        var consumer = new ClickCopyConsumer(mapper, jdbc, transaction);
        return new KafkaBatchConsumer(kafka, "click-copy", List.of(AdEvent.CLICKED), 1, batchSize,
                Duration.ofSeconds(1), consumer::copy);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "recompute.enabled", havingValue = "true")
    static class Jobs {

        @Bean
        RecomputeSql recomputeSql(ResourceLoader resources,
                @Value("${recompute.sql-location:classpath:clickhouse/recompute}") String sqlLocation,
                @Value("${clickhouse.url:}") String jdbcUrl,
                @Value("${clickhouse.username:default}") String username,
                @Value("${clickhouse.password:}") String password,
                @Value("${raw.s3.bucket:}") String bucket,
                @Value("${raw.s3.region:ap-northeast-2}") String region,
                @Value("${recompute.s3-endpoint:${raw.s3.endpoint:}}") String endpoint) {
            return new RecomputeSql(resources, sqlLocation, jdbcUrl, username, password, bucket, region, endpoint);
        }

        @Bean
        RecomputeJob recomputeJob(RecomputeSql sql, PipelineLogRepository logs) {
            return new RecomputeJob(sql, logs);
        }

        @Bean
        @Profile("!recompute-command")
        RecomputeSchedule recomputeSchedule(RecomputeJob job, RecomputeSql sql, PipelineLogRepository logs,
                Clock clock, @Value("${recompute.window-days:2}") int days) {
            return new RecomputeSchedule(job, new ReconcileJob(sql, logs), clock, days);
        }

        @Bean
        @Profile("recompute-command")
        RecomputeCommand recomputeCommand(RecomputeJob job, ConfigurableApplicationContext context) {
            return new RecomputeCommand(job, context);
        }
    }
}
