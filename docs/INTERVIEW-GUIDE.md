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
