package com.torqline.appointment.constants;

import java.time.Duration;

public final class AppointmentConstants {

    /** Booking grid: slots start on the hour or half hour. */
    public static final Duration SLOT_STEP = Duration.ofMinutes(30);

    /** Namespace for {@code pg_advisory_xact_lock(namespace, bayId)} so these locks cannot collide with others. */
    public static final int BAY_LOCK_NAMESPACE = 7001;

    /** Postgres SQLSTATE for exclusion_violation: the bay already has an overlapping booking. */
    public static final String SQLSTATE_EXCLUSION_VIOLATION = "23P01";

    /** Postgres SQLSTATE for unique_violation: here, a concurrent request with the same Idempotency-Key. */
    public static final String SQLSTATE_UNIQUE_VIOLATION = "23505";

    /** Redis key prefix for a dealer's booked intervals on one day: {@code <prefix><dealerId>:<date>}. */
    public static final String BUSY_SLOTS_CACHE_KEY_PREFIX = "torqline:busy:";

    private AppointmentConstants() {
    }
}
