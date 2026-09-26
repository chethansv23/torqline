package com.torqline.common.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Transactional outbox: instead of publishing to Kafka directly (which can succeed while the DB
 * commit fails, or vice versa), services append the event to {@code outbox_event} in the same
 * transaction as their state change. {@link OutboxRelay} ships it to Kafka afterwards.
 */
public class OutboxWriter {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public OutboxWriter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void append(String topic, String aggregateType, Object aggregateId, Object event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Outbox events must be written inside the business transaction");
        }
        jdbc.update("""
                        insert into outbox_event (id, topic, aggregate_type, aggregate_id, event_type, payload)
                        values (?, ?, ?, ?, ?, ?::jsonb)
                        """,
                UUID.randomUUID(), topic, aggregateType, aggregateId.toString(),
                event.getClass().getSimpleName(), mapper.writeValueAsString(event));
    }
}
