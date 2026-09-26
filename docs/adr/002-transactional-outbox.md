# ADR-002: Transactional outbox for domain events

**Status:** Accepted

## Context
State changes (booking, check-in, completion) must reliably produce events for other services.
Writing to Postgres and Kafka separately is a dual write: either can succeed without the other.

## Decision
Write events to an `outbox_event` table in the same transaction as the state change. A relay in each
service polls unpublished rows (`FOR UPDATE SKIP LOCKED`, batch of 100), publishes them, waits for acks,
then marks them published. Consumers deduplicate on `eventId` via `processed_event`.

## Consequences
- An event exists if and only if the state change committed.
- Delivery is at-least-once, so every consumer must be idempotent (provided by `IdempotencyGuard`).
- About 0.5 s extra latency (poll interval). CDC with Debezium would cut this and remove polling;
  it is not worth the operational cost at this scale.
- Published rows are purged after 7 days.
