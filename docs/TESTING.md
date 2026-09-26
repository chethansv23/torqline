# Testing guide

| Layer | Tools | Count | Runs in |
|---|---|---|---|
| Backend unit | JUnit 5, AssertJ, Mockito | 49 | milliseconds, no Docker |
| Backend web layer | MockMvc (standalone) | 5 | milliseconds |
| Backend integration | Spring Boot test + Testcontainers PostgreSQL | 5 | ~10 s, needs Docker |
| Frontend unit and component | Vitest, React Testing Library, jsdom | 29 | ~3 s |
| End-to-end | `scripts/demo.sh` against the running stack | 13 steps | ~10 s |
| Load and race | k6 | 2 scenarios | ~25 s |

## Running

```bash
make test            # backend + frontend
make test-backend    # ./mvnw verify, or Maven in Docker if you have no JDK
make test-web        # npm test, or Node in Docker if you have no Node
make coverage-web    # web/coverage/index.html
make demo            # needs `make up`
make loadtest        # needs `make up`
```

Backend coverage reports (JaCoCo) are written to `<module>/target/site/jacoco/index.html` by `make test-backend`.

Single tests:

```bash
./mvnw -pl appointment-service -am test -Dtest=BookingConcurrencyTest -Dsurefire.failIfNoSpecifiedTests=false
cd web && npx vitest run src/pages/BookPage.test.tsx
```

## What each backend test proves

| Test | Proves |
|---|---|
| `ServiceTypeTest` | Car and bike durations differ; vehicle-only jobs are rejected for the other type; every job fits the 30-minute grid |
| `InboundEventTest` | Kafka envelope headers decode; records without an envelope are rejected |
| `OutboxWriterTest` | Events can't be written outside the business transaction |
| `SlotGridTest` | Availability maths: last slot ends at closing, overlaps reduce free bays, other vehicle types' bookings are ignored, past slots are hidden |
| `AppointmentServiceTest` | Booking validation (grid, opening hours, past, vehicle fit, unknown dealer, no bays); idempotent replay doesn't insert; only booked appointments can be checked in or cancelled |
| `AppointmentControllerTest` | 201 new vs 200 replay; 409 carries `SLOT_UNAVAILABLE`; invalid input is a 400 with per-field errors and never reaches the service |
| **`BookingConcurrencyTest`** | Against real PostgreSQL: 50 threads racing for 2 bays → exactly 2 bookings; 50 threads for 4 bays → exactly 4, with **no deadlocks**; idempotent retry; cancelling frees the bay |
| `RepairOrderTest` | Labour pricing per vehicle; invoice with 18% GST; can't complete while parts are pending; rejected parts aren't charged; late inventory replies after cancel are ignored |
| `RepairOrderServiceTest` | RO numbering; no duplicate job card per appointment; parts requests merge and normalise SKUs; completion and cancellation publish the right events |
| `PartTest` | Reserve, release and consume arithmetic; can't over-reserve; low-stock threshold |
| `InventoryServiceTest` | All-or-nothing reservation; shortage and unknown-SKU reasons; duplicate lines summed; redelivered request ignored; consumption raises low-stock alerts; release returns stock |
| `MessageTemplatesTest`, `NotificationServiceTest` | Message wording and IST times; SMS to customer, email to manager; internal events notify nobody |

## What each frontend test proves

| Test | Proves |
|---|---|
| `format.test.ts` | Indian rupee formatting (₹1,25,000.00), labels, 12-hour times, day strip |
| `api.test.ts` | `Idempotency-Key` is sent; problem responses become `ApiError` with code and message; field errors are listed; query strings |
| `InvoiceView.test.tsx` | Subtotal, GST and total; rejected parts left off the bill |
| `BookPage.test.tsx` | Services filtered by vehicle; full slots disabled; booking sends an idempotency key and shows the confirmation; a 409 shows the error and refreshes slots; confirm disabled until a slot is picked |
| `WorkshopPage.test.tsx` | Cancelled appointments hidden; cards in the right column; check-in sends the odometer; assign sends the chosen technician; server errors are shown |
| `InventoryPage.test.tsx` | Low-stock warning; reserved quantities; fitment filter; restock |
| `InvoicePage.test.tsx` | Printable page renders and prints only when asked; warns on unfinished jobs; unknown order shows an error |
| `ActivityDrawer.test.tsx` | Lists SMS and email with the triggering event; empty state |

## Bugs the tests caught

These are real, and each is written up in the [AI review log](AI-REVIEW.md):

1. **Deadlock under contention.** The k6 race turned 46 expected `409`s into `500`s. `BookingConcurrencyTest` was
   made harder until it failed the same way, then the fix (per-bay advisory lock) was verified against it.
2. **A test that could never fail.** The first k6 script gave each virtual user its own random date, so nothing
   actually raced, yet it "passed" with 50 of 50 booked.
3. **Validation errors without details.** `AppointmentControllerTest` showed that a bad phone number returned a bare
   400 without saying which field was wrong, because Spring 7 raises a different exception when a handler also
   validates a header. The error handler now covers both.
