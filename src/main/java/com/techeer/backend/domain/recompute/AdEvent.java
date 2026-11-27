package com.techeer.backend.domain.recompute;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;

record AdEvent(JsonNode payload, LocalDateTime eventAt) {

    static final String CLICKED = "ad-clicked";
    static final String IMPRESSED = "ad-impressed";
    private static final DateTimeFormatter EVENT_TIME = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    static AdEvent parse(ObjectMapper mapper, String value) {
        if (value == null) {
            throw new IllegalArgumentException("Missing ad event");
        }
        try {
            JsonNode payload = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(value);
            JsonNode eventAt = payload.path("event_at");
            if (!eventAt.isTextual()) {
                throw new IllegalArgumentException("Missing event_at");
            }
            return new AdEvent(payload, LocalDateTime.parse(eventAt.textValue(), EVENT_TIME));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed ad event JSON");
        }
    }
}
