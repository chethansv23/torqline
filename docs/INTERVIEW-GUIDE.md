# Interview guide

Likely questions about Torqline, with short answers you can give in your own words.

## The 60-second pitch

"Torqline is a service-booking and repair-order platform for dealerships that service both cars and bikes. Customers
book a slot, the workshop checks the vehicle in, a job card opens automatically, parts are reserved from stock, and the
customer gets SMS updates and a GST invoice. It's five Spring Boot microservices with PostgreSQL, Kafka and Redis, plus a
React UI, all running with one command. The interesting part is correctness under concurrency: I guaranteed no double
booking with a database constraint, found a deadlock with a load test, fixed it, and proved the fix with tests."

## Architecture

**Why microservices and not a monolith?**
Booking, repair orders, inventory and notifications change for different reasons and scale differently; availability
reads are far hotter than invoicing. Separate services with their own databases can be deployed and scaled
independently. The trade-off is eventual consistency, which I handle with the outbox, idempotent consumers and a saga.
For a small team I'd honestly start as a modular monolith with the same boundaries.

**How do services communicate?**
Asynchronously over Kafka. For example, the appointment service publishes `AppointmentCheckedIn` and the repair-order
service reacts by opening a job card. No service calls another service's database. The gateway is the only synchronous entry point.

**Why database per service?**
Independent schema changes and deployments, and no hidden coupling through shared tables. Data another service needs
travels in events; the repair order keeps its own copy of customer and vehicle details.

## Double booking (the core question)

**How do you prevent two customers getting the same bay?**
A PostgreSQL exclusion constraint: `EXCLUDE USING gist (bay_id WITH =, tstzrange(slot_start, slot_end) WITH &&)
WHERE status <> 'CANCELLED'`. The database rejects any overlapping booking on the same bay, regardless of which
instance or code path writes it. The service tries likely-free bays in turn and returns 409 when none are left.

**Why not a Redis lock or a check in Java?**
A check-then-insert in Java has a race window. A Redis lock can be lost on failover or TTL expiry, and it still needs a
DB check. Putting the rule in the database that owns the data is the simplest thing that's always correct.

**What went wrong?**
Under a 50-user k6 race, concurrent inserts deadlocked while checking the constraint: each transaction waited on the
other's uncommitted row. I got 1 booking and 49 errors, and connection-pool starvation pushed read latency to 23 s. I
first made the integration test fail the same way (50 threads, bigger pool), then added `pg_advisory_xact_lock(bay_id)`
before the insert. Inserts for one bay now queue, and each loser fails fast with a clean violation. Result: exactly 4
bookings for 4 bays, 46 clean 409s, p95 of 2.6 ms.

**Why can't the advisory lock itself deadlock?**
Each transaction takes at most one such lock, and it's released at commit or rollback, so no cycle can form.

## Reliability

**What's the dual-write problem and how does the outbox solve it?**
Writing to the DB and to Kafka separately can leave one done and the other not, which means lost or phantom events.
I write the event to an `outbox_event` table in the same transaction as the state change. A relay polls with
`FOR UPDATE SKIP LOCKED`, publishes, waits for acks, then marks the rows published.

**Exactly-once?**
Delivery is at-least-once. Effects are exactly-once because each consumer inserts the event ID into `processed_event`
in the same transaction as its work and skips duplicates. Kafka transactions wouldn't help because they don't cover the Postgres write.

**What if a message keeps failing?**
Three retries one second apart, then it goes to a dead-letter topic (`<topic>-dlt`) for inspection.

**Idempotent booking API?**
Clients send an `Idempotency-Key` header. A repeat returns the original booking with 200 instead of 201, and a unique
constraint on the key handles two identical requests racing. Known gap: I don't yet reject a reused key with a different body.

## The parts saga

**How does it work?**
The repair order moves to PARTS_PENDING and publishes `PartsReservationRequested`. Inventory locks the part rows
(`SELECT … FOR UPDATE`, **ordered by SKU** to avoid deadlocks) and reserves everything or nothing. It replies
`PartsReserved` or `PartsReservationFailed` with a reason, and the order goes back to IN_PROGRESS. On completion, stock is
consumed. On cancellation, a compensating action releases it.

**Orchestration or choreography?**
Choreography. There are only two participants and the repair order already owns the state. I'd add an orchestrator if
payments or warranty joined the flow.

**How is ordering guaranteed?**
Repair-order events are keyed by repair-order ID, so all events for one order land on one partition in order.

**What about a late reply after a cancel?**
The aggregate ignores replies that don't match a pending request, and a unit test covers it.

## Performance and caching

**How is availability fast?**
Redis caches each branch's booked intervals per day for 60 s; availability is computed in memory from those. One key
per branch-day means one delete after any change. If Redis is down it falls back to Postgres. It measures p95 of about
3 ms at 200 req/s.

**Isn't stale cache dangerous?**
Only for display. The database decides every booking, so the worst case is a 409 and a refreshed grid.

**How would you scale it?**
All services are stateless, so add instances. The outbox relay supports several instances through `SKIP LOCKED`.
Beyond that: more Kafka partitions, read replicas for availability, and bays partitioned by dealer.

## Data and domain

**How are cars and bikes modelled?**
Bays have a vehicle type; `ServiceType` has a duration per vehicle type (null means not offered); `VehicleType` carries
the labour rate; parts have a fitment of car, bike or universal.

**Timezones?**
Stored as `timestamptz`; the API takes the dealer's local time and each dealer has a timezone.

**Migrations?**
Flyway with `ddl-auto=validate`, so the schema is reviewed SQL and Hibernate only checks it.

## Testing

**How did you test it?**
59 backend tests: unit tests (Mockito), MockMvc for the HTTP contract, and Testcontainers race tests on real Postgres.
29 frontend tests with Vitest and React Testing Library, an end-to-end script, and k6 with pass/fail thresholds.

**A bug your tests found?**
Three. The deadlock. A k6 script that could never fail, because each virtual user picked its own random date, so
nothing actually raced. And validation errors that returned no field details, because Spring 7 throws a different
exception when a controller also validates a header.

## Using AI (this posting asks about it)

**How did you use AI?**
To draft the HLD and LLD, generate code, and review designs. I logged every significant suggestion in `AI-REVIEW.md`
with how I checked it. I rejected a Redis lock, direct Kafka publishing and `ddl-auto=update`. The deadlock and the
broken load test are examples of AI output that looked right and failed under measurement.

**How do you judge whether AI output is right?**
Ask "how would this fail?", write a test that would catch it, watch it fail, then fix it. And sanity-check numbers:
50 of 50 booked with 4 bays is impossible.

## What would you do next?

Kubernetes and AWS (EKS, RDS, MSK, ElastiCache), Grafana dashboards for conflicts and outbox lag, authentication with
roles, rate limiting at the gateway, and no-show handling.

---

# Question bank

Short answers. Say them in your own words and link each back to Torqline where you can.

## A. High-level design follow-ups

**1. Walk me through the request path when a customer books.**
Browser → nginx (web) → gateway → appointment-service. It validates the request, reads cached busy slots from Redis,
then for each candidate bay opens a short transaction: advisory lock, insert appointment and outbox row, commit. It
evicts the cache and returns 201. The relay then publishes `AppointmentBooked`, and notification-service sends the SMS.

**2. What are the functional and non-functional requirements?**
Functional: availability, booking, cancel, check-in, job cards, parts reservation, invoicing, notifications.
Non-functional: zero double bookings, availability p95 under 150 ms, no lost events, tolerance of Redis or Kafka
outages, horizontal scaling, observability.

**3. Estimate the load.**
Example: 500 dealers × 10 bays × 16 slots/day is about 80k bookings/day, around 1 write/s on average and perhaps 20/s
at peak. Availability reads might be 50× that, about 1,000/s at peak. One Postgres instance handles the writes, and
Redis absorbs the reads.

**4. Where are the single points of failure and how would you remove them?**
Locally: one Postgres, one Kafka broker, one Redis. In production: RDS Multi-AZ, MSK with 3 brokers and
replication factor 3, ElastiCache with a replica, and 2+ instances of every service behind a load balancer.

**5. What happens if Kafka is down?**
Bookings still succeed; events wait in the outbox table and ship when Kafka returns. Notifications and job cards are
delayed, not lost.

**6. What happens if Redis is down?**
Availability reads go straight to Postgres; it's slower but correct. Bookings are unaffected.

**7. What happens if Postgres is down?**
That service can't serve writes and fails health checks, so no traffic is routed to it. It's the source of truth,
so that's the right failure mode rather than accepting bookings you can't guarantee.

**8. What if one service is slow?**
Others aren't blocked because they talk asynchronously. At the gateway I'd add timeouts, a circuit breaker
(Resilience4j) and rate limiting.

**9. Why is the gateway useful?**
One entry point, routing, request IDs, and a place for auth, rate limiting and CORS later.

**10. How would you add payments?**
A payment-service that consumes `RepairOrderCompleted`, creates a payment intent and publishes `PaymentCaptured`,
making it another step in the saga. At that point I'd consider an orchestrator.

**11. How would you add multi-region?**
Partition by dealer: each dealer's data lives in one home region, so bookings never need cross-region locks. Replicate
read-only data such as the catalogue.

**12. Sync vs async: when do you use which?**
Sync when the user needs the answer now (booking, availability). Async when work can happen afterwards and should
survive failures (job card, parts, SMS).

**13. Consistency model?**
Strong inside each service's database; eventual across services. The user sees PARTS_PENDING while the saga completes.

## B. Low-level design follow-ups

**14. Explain the booking schema.**
`appointment` holds bay, slot start and end as timestamptz, status, an idempotency key (unique) and a version. The
exclusion constraint on `(bay_id, tstzrange)` excludes cancelled rows, so cancelling frees the bay immediately.

**15. Why `[)` ranges?**
Half-open ranges let a 10:00-11:00 booking sit next to an 11:00-12:00 one without conflicting.

**16. Why a GiST index?**
Range overlap (`&&`) needs GiST. `btree_gist` lets it combine equality on `bay_id` with range overlap in one index.

**17. How does the service choose a bay?**
Bays that look free in the cached busy list come first; it inserts on each in turn and moves on when a bay turns out
to be taken. This keeps the number of failed attempts low.

**18. Why a separate transaction per bay attempt?**
After a constraint violation Postgres aborts the transaction, so each attempt needs a fresh one.

**19. Walk me through the repair-order state machine.**
OPEN → IN_PROGRESS → PARTS_PENDING ↔ IN_PROGRESS → COMPLETED; CANCELLED from any non-terminal state. Allowed moves
live in `RepairOrderStatus.next()`, and the aggregate refuses anything else with 409.

**20. How are invoices calculated?**
Labour = duration × rate (car ₹800/h, bike ₹400/h). Add reserved parts only, then 18% GST. It uses BigDecimal with
HALF_UP rounding, because floating point is wrong for money.

**21. Optimistic vs pessimistic locking: where and why?**
Optimistic (`@Version`) on aggregates edited by people, where conflicts are rare and should be a 409. Pessimistic
(`FOR UPDATE`) on stock rows, where contention is expected and every request must see the true quantity.

**22. How do you avoid deadlocks in inventory?**
Always lock parts in SKU order, so two transactions never hold locks in opposite orders.

**23. What's in an event?**
JSON payload keyed by aggregate ID, with `eventId` and `eventType` headers. Plain JSON keeps services independent of
each other's classes.

**24. How is schema evolution handled for events?**
Only additive changes (new optional fields); consumers ignore unknown fields. A breaking change gets a new event type
or version header. A schema registry (Avro/Protobuf) would enforce this at scale.

**25. How do you clean up the outbox?**
Published rows older than 7 days are deleted hourly.

**26. What indexes did you add and why?**
`(dealer_id, slot_start)` for a day's appointments, a partial index on unpublished outbox rows, `(dealer_id, status,
opened_at)` for the job board, and `(repair_order_id, status)` for reservations.

**27. Why Flyway with `validate`?**
Schema changes are reviewed SQL. Hibernate only checks that the entities match, so the app refuses to start with a
wrong schema instead of silently altering it.

**28. How does the error model work?**
RFC 9457 ProblemDetail with a stable `code`, so clients branch on the code, not on the message.

## C. Java and Spring Boot

**29. Why records?**
Immutable DTOs and events with equals/hashCode for free and less boilerplate.

**30. `@Transactional` vs `TransactionTemplate`: why both?**
`@Transactional` for simple service methods. `TransactionTemplate` where I need several separate transactions in one
method, like one per bay attempt, or in shared library code.

**31. Pitfalls of `@Transactional`?**
Self-invocation bypasses the proxy; it only works on public methods; checked exceptions don't roll back by default;
long transactions hold locks.

**32. What is `open-in-view` and why disable it?**
It keeps the Hibernate session open during view rendering, which hides lazy-loading problems and holds connections
longer. I turned it off.

**33. N+1 queries: how would you spot and fix them?**
Enable SQL logging or Hibernate statistics. Fix with fetch joins, entity graphs, batch fetching or DTO projections.
I used a JPQL constructor projection for busy intervals.

**34. How does the shared library get wired in?**
Spring Boot auto-configuration: `torqline-common` registers its beans through `AutoConfiguration.imports`, so every
service gets the outbox, idempotency guard and error handler just by adding the dependency.

**35. How does validation work?**
Bean Validation annotations on request records, `@Valid` on the controller, and one handler that turns both
validation exception types into the same 400 response.

**36. Virtual threads?**
Java 21 supports them (`spring.threads.virtual.enabled=true`). They help I/O-bound request handling, but DB
connections are still the limit, so the pool size matters more.

**37. How is config managed?**
`application.yml` defaults for local dev, overridden by environment variables in Docker. In the cloud: secrets from
Secrets Manager, config via environment or Spring Cloud Config.

## D. Kafka

**38. Why Kafka over RabbitMQ?**
Durable, replayable log with per-key ordering, and consumers can re-read history. RabbitMQ would also work for this
scale; Kafka fits event-driven microservices and replay.

**39. Partitions and ordering?**
Ordering is guaranteed only within a partition. Keying by aggregate ID keeps one order's events in sequence. Three
partitions allow up to three consumers per group.

**40. Consumer groups?**
Each service has its own group, so every service gets every event; instances of one service share the partitions.

**41. What is a rebalance and why does it cause duplicates?**
When consumers join or leave, partitions move. Offsets that weren't committed are re-delivered, which is why the
consumers are idempotent.

**42. `acks=all` and idempotent producer?**
`acks=all` waits for all in-sync replicas; the idempotent producer prevents duplicates caused by producer retries.

**43. Dead-letter topic: what do you do with it?**
Alert on it, inspect the message, fix the bug, and replay it to the main topic.

**44. Consumer lag: how do you monitor it?**
Kafka UI locally; in production, the consumer lag metric in Prometheus with alerts.

## E. PostgreSQL

**45. Isolation level used?**
READ COMMITTED (the default). The exclusion constraint and row locks give the guarantees; SERIALIZABLE would add
retries without adding safety here.

**46. What are advisory locks?**
Application-defined locks on a number, managed by Postgres. `pg_advisory_xact_lock` is released automatically at the
end of the transaction.

**47. `SKIP LOCKED`?**
Skips rows locked by others instead of waiting, so parallel workers each take different outbox rows.

**48. How would you scale Postgres?**
Read replicas for reads, connection pooling (PgBouncer), partitioning appointments by date or dealer, and archiving
old rows.

**49. How did you size the connection pool?**
Default Hikari pool of 10. The deadlock showed that waiting transactions hold connections, so short transactions
matter more than a bigger pool.

## F. Redis and caching

**50. Cache-aside vs write-through?**
Cache-aside (read-through): load on a miss, evict on a write. It's simple and the DB stays the source of truth.

**51. Cache stampede?**
Many requests missing at once. With a 60 s TTL per dealer-day it's small; fixes are a short lock on load or early
refresh with jitter.

**52. Why not cache the final availability?**
There are too many combinations (vehicle × service). Raw intervals give one key and one evict.

## G. Docker, Kubernetes and AWS

**53. Explain the Dockerfile.**
A multi-stage build: Maven builds the jar with a cached dependency directory, and a slim JRE image runs it as a
non-root user with memory-aware JVM flags.

**54. How would you deploy to Kubernetes?**
A Deployment and Service per microservice, readiness and liveness probes on Actuator, a HorizontalPodAutoscaler,
ConfigMaps and Secrets, Ingress for the gateway and web, and managed Postgres, Kafka and Redis.

**55. Readiness vs liveness?**
Readiness: can I take traffic now (DB reachable)? Liveness: am I stuck and should I be restarted? Mixing them up
causes restart loops.

**56. Zero-downtime deploys?**
Rolling updates with readiness probes, backward-compatible DB migrations (expand, then contract), and additive event changes.

**57. Observability?**
Metrics (Micrometer → Prometheus → Grafana), logs with a request ID, and tracing (OpenTelemetry). Key alerts: 5xx
rate, booking 409 rate, outbox lag, consumer lag, DLT count.

## H. Security

**58. How would you add authentication?**
OAuth2/OIDC (e.g. Keycloak or Cognito). The gateway validates JWTs and services check roles (customer vs advisor).

**59. What other security gaps exist today?**
No auth, no rate limiting, default DB passwords, and no PII masking in logs. Fixes: auth, a gateway rate limiter,
secrets from a vault, and masking phone numbers.

**60. SQL injection?**
Prevented by JPA and parameterised JdbcTemplate queries; no string-built SQL.

## I. Frontend

**61. Why React without Redux?**
The state is mostly server data; each screen polls the API. A library like TanStack Query would be the next step.

**62. Why polling and not WebSockets?**
Simple, and fine for a workshop board. At scale I'd push updates with Server-Sent Events or WebSockets fed from Kafka.

**63. How does the UI stay in sync after a rebuild?**
`index.html` is served with no-cache and the hashed assets are immutable.

**64. How is the frontend tested?**
Vitest and React Testing Library, with the API module mocked. Tests query by role and text, the way users see the page.

## J. Testing

**65. Unit vs integration: where's the line?**
Unit tests for pure rules (slot grid, pricing, state machine). Integration tests with Testcontainers where the
database *is* the logic (the exclusion constraint).

**66. Why Testcontainers over H2?**
H2 doesn't support exclusion constraints, GiST or advisory locks, so the key guarantee could only be tested on real Postgres.

**67. How do you test concurrency?**
Start N threads behind a latch so they fire together, then assert the exact number of successes and that every
failure is the expected 409.

**68. What would you add?**
Contract tests for events, an end-to-end test of the saga with Kafka in Testcontainers, and mutation testing.

## K. Scenario questions

**69. A customer says they were double-booked. How do you investigate?**
Check the DB: the constraint makes it impossible for one bay, so look for two bookings on different bays or a
cancelled-and-rebooked slot. Use the request ID to trace the logs, and check whether the UI showed a stale cache.

**70. Inventory shows negative stock.**
It can't: a CHECK constraint enforces `0 ≤ reserved ≤ on_hand`. Check for manual SQL edits or a failed migration.

**71. SMS messages are arriving twice.**
Check the `processed_event` records and the consumer logs for skipped duplicates. If notifications were saved twice,
the idempotency insert isn't in the same transaction as the handler.

**72. Bookings suddenly slow at 9 AM.**
Look at pool usage, lock waits (`pg_stat_activity`), the Redis hit rate and GC. Likely causes are cache misses at the
start of the day or lock contention on popular bays.

**73. Outbox table is growing.**
The relay can't publish: Kafka is down, or a poison row keeps failing the batch. Alert on unpublished row age, and
send failing rows one by one or park them.

**74. The product team wants 15-minute slots.**
Change the grid constant and the durations; the constraint works for any interval. Migrate existing data if needed.

**75. A dealer adds a bay.**
Insert a row into `service_bay`. It appears in availability once the cache for that day expires or is evicted.

## L. Behavioural

**76. Hardest problem in this project?**
The deadlock. I didn't guess at a fix; I reproduced it in a test first, read the Postgres log, fixed the cause, and
measured before and after.

**77. A trade-off you made?**
Eventual consistency for job cards and SMS in exchange for independent services, with outbox and idempotency so
nothing is lost.

**78. What would you do differently?**
Start as a modular monolith, add auth from day one, and add contract tests for events earlier.

**79. How do you mentor juniors using a project like this?**
ADRs to explain why, tests that document behaviour, and the AI review log to show how to question generated code.

**80. How do you review code?**
Correctness first (races, transactions, error paths), then tests, then readability. I ask "how does this fail?" for
every external call.
