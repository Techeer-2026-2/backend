package com.techeer.backend.domain.recompute;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.util.Assert;

final class RecomputeSql {

    private static final Pattern PARAMETER = Pattern.compile("\\{\\{([a-z_][a-z0-9_]*)}}");
    private final JdbcTemplate jdbc;
    private final String endpoint;
    private final String archiveCounts;
    private final String dailyCount;
    private final String replace;

    RecomputeSql(ResourceLoader resources, String sqlLocation, String jdbcUrl, String username, String password,
            String bucket, String region, String s3Endpoint) {
        Assert.hasText(jdbcUrl, "clickhouse.url is required for recompute");
        Assert.isTrue(bucket.matches("[a-z0-9][a-z0-9.-]*"), "A valid raw.s3.bucket is required for recompute");
        Assert.isTrue(region.matches("[a-z0-9-]+"), "A valid raw.s3.region is required for recompute");
        var dataSource = new DriverManagerDataSource(jdbcUrl, username, password);
        jdbc = new JdbcTemplate(dataSource);
        if (s3Endpoint.isBlank()) {
            endpoint = bucket.contains(".")
                    ? "https://s3." + region + ".amazonaws.com/" + bucket
                    : "https://" + bucket + ".s3." + region + ".amazonaws.com";
        } else {
            URI uri = URI.create(s3Endpoint);
            Assert.isTrue(("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                            && uri.getHost() != null && uri.getQuery() == null && uri.getFragment() == null
                            && uri.getUserInfo() == null,
                    "The ClickHouse S3 endpoint must be an HTTP(S) URL without credentials or query parameters");
            endpoint = uri.toASCIIString().replaceAll("/+$", "") + "/" + bucket;
        }
        archiveCounts = read(resources, sqlLocation, "archive-counts.sql");
        dailyCount = read(resources, sqlLocation, "daily-count.sql");
        replace = read(resources, sqlLocation, "replace-partition.sql");
    }

    ArchiveCounts archive(LocalDate date, EventKind kind) {
        return jdbc.queryForObject(render(archiveCounts, date, kind),
                (rs, row) -> new ArchiveCounts(rs.getInt("file_count"), rs.getLong("line_count"),
                        rs.getLong("unique_count")));
    }

    long daily(LocalDate date, EventKind kind) {
        return jdbc.queryForObject(render(dailyCount, date, kind), Long.class);
    }

    void replace(LocalDate date, EventKind kind) {
        var script = new ByteArrayResource(render(replace, date, kind).getBytes(StandardCharsets.UTF_8),
                "replace-partition.sql");
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection, script);
            return null;
        });
    }

    private String render(String sql, LocalDate date, EventKind kind) {
        var params = Map.of(
                "date", quote(date.toString()),
                "partition", DateTimeFormatter.BASIC_ISO_DATE.format(date),
                "s3_url", quote(endpoint + "/" + RawBatchBuffer.dayGlob(kind.topic, date)),
                "facts", kind.facts);
        return PARAMETER.matcher(sql).replaceAll(match -> parameter(params, match));
    }

    private String parameter(Map<String, String> params, MatchResult match) {
        String value = params.get(match.group(1));
        if (value == null) {
            throw new IllegalArgumentException("Unknown ClickHouse SQL parameter: " + match.group(1));
        }
        return Matcher.quoteReplacement(value);
    }

    private static String quote(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'";
    }

    private static String read(ResourceLoader resources, String location, String file) {
        String path = location.replaceAll("/+$", "") + "/" + file;
        try {
            String sql = resources.getResource(path).getContentAsString(StandardCharsets.UTF_8);
            Assert.hasText(sql, "Empty ClickHouse SQL resource: " + path);
            return sql;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Missing ClickHouse SQL resource: " + path, e);
        }
    }

    enum EventKind {
        CLICK(AdEvent.CLICKED, "click_facts"),
        IMPRESSION(AdEvent.IMPRESSED, "impression_facts");

        private final String topic;
        private final String facts;

        EventKind(String topic, String facts) {
            this.topic = topic;
            this.facts = facts;
        }
    }

    record ArchiveCounts(int files, long lines, long unique) {
    }
}
