package com.torqline.common.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** A consumed Kafka record with its envelope headers decoded. */
public record InboundEvent(UUID eventId, String eventType, String payload) {

    public static InboundEvent from(ConsumerRecord<String, String> record) {
        return new InboundEvent(UUID.fromString(header(record, EventHeaders.EVENT_ID)),
                header(record, EventHeaders.EVENT_TYPE), record.value());
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null) {
            throw new IllegalArgumentException("Missing header '" + name + "' on " + record.topic() + "@" + record.offset());
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    public boolean is(Class<?> type) {
        return type.getSimpleName().equals(eventType);
    }

    public <T> T as(ObjectMapper mapper, Class<T> type) {
        return mapper.readValue(payload, type);
    }
}
