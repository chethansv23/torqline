# ADR-001: Prevent double booking in Postgres

**Status:** Accepted

## Context
Bookings can arrive at the same moment from many customers and from several instances of
appointment-service. A bay must never hold two overlapping live bookings.

## Options
1. **Application check then insert:** a race window between check and insert.
2. **Redis lock per slot:** extra moving part; can lose the lock on failover or TTL expiry; still needs a DB check.
3. **`SELECT ... FOR UPDATE` on the bay row:** works, but serialises all reads of the bay, and the rule lives only in code.
4. **Postgres exclusion constraint** on `(bay_id WITH =, tstzrange(slot_start, slot_end) WITH &&) WHERE status <> 'CANCELLED'`.

## Decision
Option 4, with a per-bay `pg_advisory_xact_lock` taken just before the insert.

## Consequences
- The rule holds for every writer: bugs, scripts, a second service instance.
- Losing a race is a normal, fast `23P01` that the service turns into "try the next bay" or `409`.
- The advisory lock is needed because concurrent inserts that conflict under an exclusion constraint
  can deadlock (found under load; see AI-REVIEW #2). It serialises inserts per bay only; throughput
  across bays and dealers is unaffected.
- Requires the `btree_gist` extension (available on RDS/Aurora Postgres).
