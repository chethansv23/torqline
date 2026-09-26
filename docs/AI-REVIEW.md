# AI-assisted design and review log

This project was designed and built with an AI coding assistant. This log records what the AI proposed,
how each proposal was checked, and the decision. The aim is to show judgement: which suggestions were
accepted, which were changed, and which were wrong and how that was found.

> **Before sharing:** rewrite these entries in your own words, and add your own as you extend the
> project. Be ready to explain each one in an interview. Entries 2 and 3 describe real failures from
> building this project.

| # | Area | AI proposal | How it was checked | Verdict |
|---|---|---|---|---|
| 1 | Double booking | Redis distributed lock (`SETNX` per slot) around the booking | Asked what happens on Redis failover or lock-TTL expiry during a slow insert: the lock can be lost and two writers proceed. The DB is the source of truth anyway | **Rejected.** Postgres exclusion constraint on `(bay_id, tstzrange)`. [ADR-001](adr/001-double-booking-prevention.md) |
| 2 | Double booking | The exclusion constraint alone is enough under concurrency | k6 race: 50 users, 4 bays. **1 booked, 49 failed with HTTP 500**, and availability p95 jumped to 23 s. Postgres log: `deadlock detected ... while checking exclusion constraint`. The 20-thread test had passed because its 10-connection pool limited real parallelism | **Changed.** Added a per-bay `pg_advisory_xact_lock`. Raised the test to 50 threads with a 60-connection pool plus a 4-bay case; confirmed it **fails without the lock** and passes 3/3 with it. k6 afterwards: 4 booked, 46 clean 409s, p95 2.6 ms |
| 3 | Load test | k6 script picked a random date in init code | First run reported **all 50 racers booked**, which is impossible with 4 bays. k6 runs init code once per VU, so each VU raced on its own day | **Fixed the test.** Date chosen in `setup()` and shared. Lesson: a passing test can be wrong, so check the number actually makes sense |
| 4 | Events | Save the entity, then `kafkaTemplate.send()` in the same method | Walked through a crash between commit and send (event lost) and a send followed by rollback (phantom event) | **Rejected.** Transactional outbox + relay with `SKIP LOCKED`. [ADR-002](adr/002-transactional-outbox.md) |
| 5 | Events | Exactly-once via Kafka transactions | Kafka transactions do not cover the Postgres write, so they don't remove the dual write | **Rejected.** At-least-once delivery + idempotent consumers (`processed_event`) |
| 6 | Caching | `@Cacheable` on the availability method keyed by (dealer, vehicle, service, date) | Eviction after a booking would need to clear every vehicle/service combination for that day | **Changed.** Cache the raw busy intervals per dealer-day. One key, one evict, availability computed in memory |
| 7 | Caching | Evict inside the booking transaction | Evicting before commit lets a concurrent reader repopulate with pre-commit data | **Changed.** Evict after commit. Remaining small race documented in LLD section 7; acceptable because the DB decides |
| 8 | Inventory | Check stock, then `UPDATE` per line | Two concurrent requests can both pass the check (check-then-act) | **Changed.** `SELECT ... FOR UPDATE` on all SKUs **in SKU order** (avoids deadlocks), plus a CHECK constraint `reserved <= on_hand` |
| 9 | Saga | Separate orchestrator service | Only two participants and the repair order already owns the state | **Rejected for now.** Choreography with RO state machine; revisit if payments or warranty join |
| 10 | Schema | `spring.jpa.hibernate.ddl-auto=update` | Unreviewable, can't express exclusion constraints, risky in prod | **Rejected.** Flyway migrations + `ddl-auto=validate` |
| 11 | Idempotency | Store key only | A client reusing a key with a *different* body silently gets the old booking | **Accepted with known gap.** Next step: store a request hash and return 422 on mismatch |
| 12 | Late replies | Apply `PartsReserved` whenever it arrives | Cancel then late reserve would reopen a cancelled job | **Changed.** Aggregate ignores replies not matching a pending request; unit-tested |
| 13 | Error handling | One `@ExceptionHandler` for `MethodArgumentNotValidException` covers all validation | A MockMvc test sent a bad phone number and got a bare 400 with no field details. With `@Size` on the `Idempotency-Key` header, Spring 7 runs method validation and throws `HandlerMethodValidationException` instead | **Fixed.** Handle both exceptions with one response shape; the controller test guards it |
| 14 | UI | Print the invoice by hiding the app with `@media print` while the dialog is open | The user's printout was blank: the dialog lived inside the hidden app. Moving it to a portal worked in the preview, but the browser was still serving a cached bundle | **Changed.** A dedicated `#/invoice/<id>` page, verified by printing to PDF in headless Chrome; `Cache-Control: no-cache` on `index.html` |

## Prompts that worked well

- "List every way this can double-book, including across two instances of the service."
- "What happens if the process dies between line X and line Y?"
- "Write a test that fails if this guarantee is broken, and show me it failing first."
- "Is this number plausible?" (asked about the 50/50 booked result, which exposed the bad test)
