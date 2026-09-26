package com.torqline.appointment.slot;

import java.time.Instant;

/** A bay occupied for [start, end). */
public record BusyInterval(Long bayId, Instant start, Instant end) {

    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        return start.isBefore(otherEnd) && otherStart.isBefore(end);
    }
}
