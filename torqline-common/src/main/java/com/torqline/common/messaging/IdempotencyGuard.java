package com.torqline.common.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Idempotent consumer. Records the event id in {@code processed_event} in the same transaction
 * as the handler's work, so a redelivered event (outbox retry, consumer rebalance) is skipped
 * instead of being applied twice.
 */
public class IdempotencyGuard {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyGuard.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public IdempotencyGuard(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    /** @return true if the handler ran, false if the event had already been processed */
    public boolean processOnce(UUID eventId, Runnable handler) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            int inserted = jdbc.update(
                    "insert into processed_event (event_id) values (?) on conflict do nothing", eventId);
            if (inserted == 0) {
                log.info("Skipping duplicate event {}", eventId);
                return false;
            }
            handler.run();
            return true;
        }));
    }
}
