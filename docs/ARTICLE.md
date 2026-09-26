# Building a double-booking-proof service platform for car and bike workshops

*How I designed, built and load-tested an event-driven dealership system in Java, and the two bugs that only
showed up when I measured it.*

---

Walk into any multi-brand service centre in Bengaluru on a Saturday morning and you'll see the same thing: a row of
car lifts, a row of bike stands, a service advisor with a phone in each hand, and a whiteboard that's never quite
right. Two customers were told "10 o'clock is fine". A job card says brake pads, but the last set went to another
car an hour ago. Someone's bike was ready at noon, but nobody told them.

I wanted to build the software that makes those problems impossible rather than just unlikely. The result is
**Torqline**: customers book a slot for a car or a bike, advisors run the workshop from a live job board, parts are
reserved before work continues, and customers get SMS updates and an itemised GST invoice.

This article covers the design choices, the tools, and the part I learned the most from: the two bugs that every
unit test passed but a load test exposed.

## Cars and bikes are not the same customer

The first design decision was to model the two kinds of vehicle honestly instead of adding a `type` column and
moving on:

- **Different bays.** A hatchback doesn't fit on a bike stand. Every bay has a vehicle type, and a booking can only
  land on a bay of the same type.
- **Different job lengths.** A general service is 2 hours for a car and 1 hour for a bike, so availability depends
  on the vehicle as well as the service.
- **Different jobs.** Bikes need chain and sprocket kits; cars need wheel alignment and AC service. The API refuses
  a wheel alignment for a scooter, and the UI never offers it.
- **Different money.** Labour is ₹800/hour for cars and ₹400/hour for bikes, and parts have a fitment of car, bike
  or universal.

All of this lives in two small Java enums shared by every service. That's the kind of domain knowledge that belongs
in code rather than in a wiki.

## The architecture

Torqline is five Spring Boot services behind a gateway, plus a React front end:

- **appointment-service**: dealers, bays, availability and bookings
- **repair-order-service**: job cards and their workflow
- **parts-inventory-service**: stock and reservations
- **notification-service**: SMS and email
- **gateway**: one entry point

Each service owns its own PostgreSQL database, and they coordinate through Kafka. When an advisor checks a
vehicle in, the appointment service doesn't call the repair-order service. It records an `AppointmentCheckedIn`
event, and the repair-order service reacts by opening a job card. When the job needs parts, a small **saga** runs:
the repair order asks for stock, inventory either reserves all of it or none of it, and the order moves on. If the
job is cancelled, inventory releases what it held.

Two patterns carry most of the reliability:

**The transactional outbox.** Saving a booking and publishing "booking confirmed" to Kafka are two separate writes,
and either can fail on its own. You then either confirm a booking that doesn't exist or never confirm one that
does. So each service writes its events to an `outbox_event` table **in the same transaction** as its data, and a
relay publishes them afterwards using `SELECT … FOR UPDATE SKIP LOCKED` so several instances can share the work.

**Idempotent consumers.** The relay can send an event twice (that's the price of never losing one), so every
consumer records the event ID in the same transaction as its work and skips anything it has already seen.

## The hardest problem: two people, one bay

Double booking is the problem customers notice first, and it's harder than it looks. "Check if the slot is free,
then insert" has a gap between the two steps, and with two app instances behind a load balancer, an in-memory
lock doesn't help.

I pushed the rule down into PostgreSQL with an **exclusion constraint**:

```sql
constraint ex_appointment_bay_overlap exclude using gist (
    bay_id with =,
    tstzrange(slot_start, slot_end, '[)') with &&
) where (status <> 'CANCELLED')
```

In plain English: *no two live bookings on the same bay may have overlapping time ranges.* The database enforces it
for every writer, whether that's a buggy code path, a second instance or a hand-written SQL script. The service
tries the likely-free bays in turn; losing a race is just an error code that means "try the next bay", and when none
are left the customer gets a clean `409 Slot unavailable`.

I wrote an integration test using Testcontainers: 20 threads, one slot, 2 bike stands, and exactly 2 bookings
expected. It passed. I was happy.

## Bug 1: the test that couldn't fail

Then I wrote a k6 load test: 50 virtual users booking the same bike slot at a branch with 4 bike stands. The
result came back **50 bookings out of 50**.

That's impossible with 4 stands, which made it the most useful number of the day. The bug was in the test: k6
runs your initialisation code once *per virtual user*, and I'd picked the date with `Math.random()` there. Every
user was booking a different day, so nothing competed for anything.

**Lesson:** a test that can't fail proves nothing. Check that the number you got is actually possible.

## Bug 2: the deadlock that only appears at scale

With the date fixed so all 50 users really competed, the result was worse: **1 booking, 49 HTTP 500 errors, and
availability reads slowed to 23 seconds.**

The Postgres log explained it:

```
ERROR: deadlock detected
DETAIL: Process 107 waits for ShareLock on transaction 916; blocked by process 115.
        Process 115 waits for ShareLock on transaction 918; blocked by process 107.
CONTEXT: while checking exclusion constraint on tuple (2,6) in relation "appointment"
```

When two transactions insert conflicting rows at the same instant, each one checks the constraint, sees the other's
uncommitted row, and waits for it to finish. Both wait, forever, until Postgres picks a victim after its one-second
deadlock timeout. Meanwhile every waiting request holds a database connection, so the pool runs dry and even simple
reads queue behind them.

My 20-thread test had missed it because its default 10-connection pool quietly limited how many inserts ran at once.
So before fixing anything, I made the test fail: 50 threads, a 60-connection pool, and the same 4-bay scenario k6
used. It failed with `deadlock detected`, which proved it was now a real regression test.

The fix is one line before the insert:

```java
jdbc.query("select pg_advisory_xact_lock(?, ?)", rs -> null, BAY_LOCK_NAMESPACE, bay.getId().intValue());
```

A transaction-scoped advisory lock *per bay* makes inserts for the same bay queue briefly instead of deadlocking.
Each one after the winner then fails fast with a clean constraint violation. Each transaction holds at most one such
lock, so no cycle can form, and different bays and branches don't wait for each other at all. The constraint is
still what guarantees correctness; the lock only removes the deadlock.

After the fix:

| | Before | After |
|---|---|---|
| Bookings for 4 bays under a 50-way race | 1 | **4** |
| Clean `409 Slot unavailable` | 0 | **46** |
| Server errors | 49 | **0** |
| Availability p95 at 200 req/s | 23 s | **2.6 ms** |

## Keeping reads fast

Customers browse availability far more often than they book, so the appointment service caches each branch's booked
intervals per day in Redis for 60 seconds and computes availability in memory. Caching raw intervals rather than
finished answers means one cache key per branch-day, and one delete after any booking.

The cache is only an optimisation. If it's slightly stale, a customer might see a slot that's just been taken; the
booking then fails cleanly with a 409 and the grid refreshes. If Redis is down, reads fall back to PostgreSQL. The
database always makes the final decision.

## The front end

The UI is React 19 with TypeScript, with a screen for each role: **Book service** for customers, **Workshop** for
advisors (a live board from *Checked in* to *Ready for pickup*), and **Inventory** for the parts desk. A bell icon
shows every SMS and email the system has sent, which is a nice way to *see* Kafka events arriving.

Even printing an invoice had a lesson in it. My first attempt printed the invoice dialog by hiding everything else
with print CSS, and it produced a blank page, because the dialog lived inside the part being hidden. The robust
answer was a dedicated printable invoice page, which I verified by printing to PDF in headless Chrome rather than
trusting a screenshot.

## Working with an AI assistant

I used an AI coding assistant for much of the design and implementation, and I kept a
[review log](AI-REVIEW.md) of its suggestions and what I did with each. Some were good and went in as written.
Several were rejected: a Redis lock for booking (it can be lost on failover), publishing to Kafka straight after
saving (the dual-write problem), and `ddl-auto=update` instead of reviewed migrations. The most valuable entries
are the ones where the suggestion *looked* right and only measurement showed otherwise, like the deadlock.

The skill that mattered most wasn't writing code faster. It was asking "how would I know if this is wrong?" and
then building the test that answers it.

## Testing

- **59 backend tests**: unit tests for the domain rules and pricing, MockMvc tests for the HTTP contract, and
  Testcontainers races against a real PostgreSQL database
- **29 front-end tests** with Vitest and React Testing Library
- a 13-step **end-to-end script** that books, checks in, runs the parts saga both ways and invoices
- a **k6** race-and-throughput test with pass/fail thresholds

## Try it

Everything runs locally with Docker; you don't need Java, Maven or Node installed:

```bash
make up      # then open http://localhost:3000
```

## What I'd do next

- deploy to AWS with Kubernetes (EKS, RDS, MSK, ElastiCache)
- dashboards for booking conflicts, outbox lag and consumer lag
- sign-in with customer and advisor roles
- no-show handling that frees a bay automatically

---

*Tech stack: Java 21, Spring Boot 4, Spring Cloud Gateway, Hibernate 7, Flyway, PostgreSQL 17, Apache Kafka 3.9,
Redis 7, React 19, TypeScript, Vite, Vitest, JUnit 5, Mockito, Testcontainers, k6, Docker Compose, GitHub Actions.*
