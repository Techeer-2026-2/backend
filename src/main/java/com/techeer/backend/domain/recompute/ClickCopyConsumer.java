package com.techeer.backend.domain.recompute;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

final class ClickCopyConsumer {

    private static final Logger log = LoggerFactory.getLogger(ClickCopyConsumer.class);
    private static final String INSERT = """
            INSERT INTO click_events (notification_id, campaign_id, target_user_id, clicked_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (notification_id) DO NOTHING
            """;

    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    ClickCopyConsumer(ObjectMapper mapper, JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    void copy(List<ConsumerRecord<String, String>> records) {
        var rows = new ArrayList<Object[]>();
        for (ConsumerRecord<String, String> record : records) {
            AdEvent event = AdEvent.parse(mapper, record.value());
            var user = event.payload().path("target_user_id");
            if (user.isMissingNode() || user.isNull()) {
                log.warn("Click copy skipped a message without target_user_id (partition={}, offset={})",
                        record.partition(), record.offset());
                continue;
            }
            var notification = event.payload().path("notification_id");
            var campaign = event.payload().path("campaign_id");
            if (!notification.isTextual() || !campaign.isIntegralNumber() || !campaign.canConvertToLong()
                    || !user.isIntegralNumber() || !user.canConvertToLong()) {
                throw new IllegalArgumentException("Invalid click copy fields (partition=" + record.partition()
                        + ", offset=" + record.offset() + ")");
            }
            rows.add(new Object[]{notification.textValue(), campaign.longValue(), user.longValue(),
                    event.eventAt(), event.eventAt(), event.eventAt()});
        }
        if (!rows.isEmpty()) {
            transaction.executeWithoutResult(status -> jdbc.batchUpdate(INSERT, rows));
        }
    }
}
