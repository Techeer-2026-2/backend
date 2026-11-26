package com.techeer.backend.domain.recompute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Testcontainers
class RecomputePipelineIntegrationTest {

    private static final Network NETWORK = Network.newNetwork();
    private static final String BUCKET = "raw-events";
    private static final String MINIO_USER = "test-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String MINIO_PASSWORD = UUID.randomUUID().toString();
    private static final Clock CLOCK = Clock.system(ZoneId.of("Asia/Seoul"));

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    private static final GenericContainer<?> MINIO = new GenericContainer<>("bitnamilegacy/minio:2025.4.22-debian-12-r0")
            .withNetwork(NETWORK)
            .withNetworkAliases("archive")
            .withEnv("MINIO_ROOT_USER", MINIO_USER)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASSWORD)
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
    @Container
    private static final GenericContainer<?> CLICKHOUSE = new GenericContainer<>("clickhouse/clickhouse-server:25.8-alpine")
            .withNetwork(NETWORK)
            .withEnv("CLICKHOUSE_SKIP_USER_SETUP", "1")
            .withExposedPorts(8123)
            .waitingFor(Wait.forHttp("/ping").forPort(8123));

    @Test
    void archivesEventsReplacesRollupsIdempotentlyAndDetectsArchiveDrift() {
        LocalDate day = LocalDate.now(CLOCK).minusDays(1);
        var postgres = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var pg = new JdbcTemplate(postgres);
        initPostgres(postgres, pg, day);
        var logs = new PipelineLogRepository(pg, postgres, CLOCK);
        var mapper = new ObjectMapper();
        var buffer = new RawBatchBuffer(mapper);
        String click = event(day, UUID.randomUUID(), ",\"target_user_id\":7");
        String impression = event(day, UUID.randomUUID(), "");
        var records = List.of(
                new ConsumerRecord<>(AdEvent.CLICKED, 0, 0, "1", click),
                new ConsumerRecord<>(AdEvent.CLICKED, 0, 1, "1", click),
                new ConsumerRecord<>(AdEvent.IMPRESSED, 0, 0, "1", impression));

        var failingWriter = mock(S3RawWriter.class);
        doThrow(new IllegalStateException("Test upload failure")).when(failingWriter).upload(any());
        var failingArchive = new RawArchiveConsumer(buffer, failingWriter, logs);
        assertThatThrownBy(() -> failingArchive.archive(records)).hasMessage("Test upload failure");
        assertThat(logs.archives(day).consumed()).isZero();

        try (S3Client s3 = minioClient()) {
            initBucket(s3);
            var writer = new S3RawWriter(s3, BUCKET);
            new RawArchiveConsumer(buffer, writer, logs).archive(records);
            assertThat(logs.archives(day).written()).isEqualTo(3);

            String url = "jdbc:clickhouse://" + CLICKHOUSE.getHost() + ":" + CLICKHOUSE.getMappedPort(8123) + "/default";
            var ch = new JdbcTemplate(new DriverManagerDataSource(url, "default", ""));
            initFacts(ch);
            var sql = new RecomputeSql(new DefaultResourceLoader(), "classpath:clickhouse/recompute-fixture", url,
                    "default", "", BUCKET, "us-east-1", "http://archive:9000");
            var job = new RecomputeJob(sql, logs);
            assertThat(job.run(day, day)).isTrue();
            assertThat(job.run(day, day)).isTrue();
            assertThat(pg.queryForObject("SELECT count(*) FROM recompute_log WHERE target_date <> ?", Long.class, day))
                    .isZero();
            assertThat(pg.queryForObject("SELECT duplicate_count FROM recompute_log WHERE recompute_id = ?",
                    Long.class, logs.latestRun(day).id())).isEqualTo(1);
            for (String table : List.of("click_facts", "click_facts_hour", "click_facts_day", "impression_facts",
                    "impression_facts_hour", "impression_facts_day")) {
                assertThat(ch.queryForObject("SELECT sum(cnt) FROM " + table, Long.class)).isEqualTo(1);
            }

            var reconcile = new ReconcileJob(sql, logs);
            reconcile.run(day, day);
            assertThat(reconcileOk(pg, logs.latestRun(day).id())).isTrue();
            String clickFile = s3.listObjectsV2(req -> req.bucket(BUCKET).prefix("raw-clicks/")).contents().getFirst().key();
            s3.deleteObject(req -> req.bucket(BUCKET).key(clickFile));
            reconcile.run(day, day);
            assertThat(reconcileOk(pg, logs.latestRun(day).id())).isFalse();
        }
    }

    private void initPostgres(DriverManagerDataSource source, JdbcTemplate pg, LocalDate day) {
        new ResourceDatabasePopulator(new FileSystemResource("docs/sql/ad-pipeline/004_recompute_log.sql")).execute(source);
        pg.execute("CREATE TABLE ad_impressions (shown_at TIMESTAMP NOT NULL)");
        pg.update("INSERT INTO ad_impressions (shown_at) VALUES (?)", day.atTime(8, 0));
    }

    private S3Client minioClient() {
        return S3Client.builder().region(Region.US_EAST_1).forcePathStyle(true)
                .endpointOverride(URI.create("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000)))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(MINIO_USER, MINIO_PASSWORD)))
                .build();
    }

    private void initBucket(S3Client s3) {
        s3.createBucket(req -> req.bucket(BUCKET));
        // The test-only bucket is anonymous-read on the isolated Docker network so ClickHouse needs no embedded key.
        s3.putBucketPolicy(req -> req.bucket(BUCKET).policy("""
                {"Version":"2012-10-17","Statement":[
                  {"Effect":"Allow","Principal":"*","Action":"s3:ListBucket","Resource":"arn:aws:s3:::%s"},
                  {"Effect":"Allow","Principal":"*","Action":"s3:GetObject","Resource":"arn:aws:s3:::%s/*"}
                ]}
                """.formatted(BUCKET, BUCKET)));
    }

    private void initFacts(JdbcTemplate ch) {
        for (String base : List.of("click_facts", "impression_facts")) {
            createFacts(ch, base, "minute", "DateTime");
            createFacts(ch, base + "_hour", "hour", "DateTime");
            createFacts(ch, base + "_day", "day", "Date");
        }
    }

    private void createFacts(JdbcTemplate ch, String table, String bucket, String dateType) {
        ch.execute("""
                CREATE TABLE %s (campaign_id Int64, %s %s, age_group String, gender String, cnt UInt64)
                ENGINE = SummingMergeTree PARTITION BY toYYYYMMDD(%s) ORDER BY (campaign_id, %s, age_group, gender)
                """.formatted(table, bucket, dateType, bucket, bucket));
        ch.execute("CREATE TABLE " + table + "_stage AS " + table);
    }

    private Boolean reconcileOk(JdbcTemplate pg, long id) {
        return pg.queryForObject("SELECT reconcile_ok FROM recompute_log WHERE recompute_id = ?", Boolean.class, id);
    }

    private String event(LocalDate day, UUID notification, String extra) {
        return """
                {"notification_id":"%s","campaign_id":1,"age_group":"20s","gender":"female","event_at":"%s 08:00:00"%s}
                """.formatted(notification, day, extra).strip();
    }
}
