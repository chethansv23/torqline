# ADR-003: Database per service

**Status:** Accepted

## Context
Services need to deploy and change schema independently.

## Decision
Each service owns one Postgres database and is the only one that reads or writes it. Locally, all four
databases share one Postgres container; in the cloud they can share one RDS instance or be split.

## Consequences
- No cross-service joins. Data another service needs travels in events (for example, the repair order
  keeps its own copy of customer and vehicle details from `AppointmentCheckedIn`).
- Cross-service consistency is eventual and handled with sagas and compensation.
- Reporting across services would need a read model or warehouse (out of scope).
