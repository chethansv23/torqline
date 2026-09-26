package com.torqline.common.messaging;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Polls unpublished outbox rows and sends them to Kafka. {@code FOR UPDATE SKIP LOCKED} lets
 * several instances of a service relay in parallel without sending the same row twice at once.
 * Delivery is at-least-once: if the batch fails after some sends, the whole batch is retried,
 * which is why every consumer goes through {@link IdempotencyGuard}.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private record PendingEvent(UUID id, String topic, String aggregateId, String eventType, String payload) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String, String> kafka;
    private final int batchSize;

    public OutboxRelay(JdbcTemplate jdbc, TransactionTemplate tx, KafkaTemplate<String, String> kafka, int batchSize) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.kafka = kafka;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${torqline.outbox.poll-interval-ms:500}")
    public void publishPending() {
        try {
            Integer sent = tx.execute(status -> relayBatch());
            if (sent != null && sent > 0) {
                log.debug("Relayed {} outbox events", sent);
            }
        } catch (RuntimeException e) {
            log.warn("Outbox relay failed, will retry: {}", e.getMessage());
        }
    }

    private int relayBatch() {
        List<PendingEvent> batch = jdbc.query("""
                        select id, topic, aggregate_id, event_type, payload::text
                        from outbox_event
                        where published_at is null
                        order by created_at
                        limit ?
                        for update skip locked
                        """,
                (rs, i) -> new PendingEvent(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5)),
                batchSize);
        if (batch.isEmpty()) {
            return 0;
        }
        CompletableFuture<?>[] sends = batch.stream().map(this::send).toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(sends).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Kafka send failed", e);
        }
        jdbc.batchUpdate("update outbox_event set published_at = now() where id = ?",
                batch.stream().map(e -> new Object[]{e.id()}).toList());
        return batch.size();
    }

    private CompletableFuture<?> send(PendingEvent event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(event.topic(), event.aggregateId(), event.payload());
        record.headers()
                .add(EventHeaders.EVENT_ID, event.id().toString().getBytes(StandardCharsets.UTF_8))
                .add(EventHeaders.EVENT_TYPE, event.eventType().getBytes(StandardCharsets.UTF_8));
        return kafka.send(record);
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    public void purgePublished() {
        int purged = jdbc.update("delete from outbox_event where published_at < now() - interval '7 days'");
        if (purged > 0) {
            log.info("Purged {} published outbox events", purged);
        }
    }
}
