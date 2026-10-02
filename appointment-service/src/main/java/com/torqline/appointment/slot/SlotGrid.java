package com.torqline.appointment.slot;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.torqline.appointment.constants.AppointmentConstants.SLOT_STEP;

/**
 * Pure availability calculation on a fixed 30-minute grid. Kept free of Spring and the database
 * so the rules are easy to unit test.
 */
public final class SlotGrid {

    public record Slot(LocalTime start, LocalTime end, int freeBays) {
    }

    private SlotGrid() {
    }

    public static boolean isOnGrid(LocalTime time) {
        return time.getSecond() == 0 && time.getNano() == 0 && time.getMinute() % SLOT_STEP.toMinutes() == 0;
    }

    /**
     * @param bayIds bays able to take this vehicle type
     * @param busy   everything already booked at the dealer on that day
     * @param now    slots starting before this instant are not offered
     */
    public static List<Slot> compute(LocalDate date, ZoneId zone, LocalTime open, LocalTime close, Duration duration,
                                     List<Long> bayIds, List<BusyInterval> busy, Instant now) {
        List<Slot> slots = new ArrayList<>();
        long window = Duration.between(open, close).toMinutes();
        for (long offset = 0; offset + duration.toMinutes() <= window; offset += SLOT_STEP.toMinutes()) {
            LocalTime t = open.plusMinutes(offset);
            Instant start = ZonedDateTime.of(date, t, zone).toInstant();
            Instant end = start.plus(duration);
            if (start.isBefore(now)) {
                continue;
            }
            int free = (int) bayIds.stream()
                    .filter(bay -> busy.stream().noneMatch(b -> b.bayId().equals(bay) && b.overlaps(start, end)))
                    .count();
            slots.add(new Slot(t, t.plus(duration), free));
        }
        return slots;
    }
}
