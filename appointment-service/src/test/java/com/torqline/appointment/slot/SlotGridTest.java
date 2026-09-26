package com.torqline.appointment.slot;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlotGridTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate DAY = LocalDate.of(2030, 1, 7);
    private static final Instant LONG_AGO = Instant.EPOCH;

    private static Instant at(int hour, int minute) {
        return ZonedDateTime.of(DAY, LocalTime.of(hour, minute), IST).toInstant();
    }

    @Test
    void lastSlotEndsExactlyAtClosingTime() {
        List<SlotGrid.Slot> slots = SlotGrid.compute(DAY, IST, LocalTime.of(9, 0), LocalTime.of(18, 0),
                Duration.ofMinutes(120), List.of(1L), List.of(), LONG_AGO);

        assertThat(slots).first().extracting(SlotGrid.Slot::start).isEqualTo(LocalTime.of(9, 0));
        assertThat(slots).last().extracting(SlotGrid.Slot::end).isEqualTo(LocalTime.of(18, 0));
        assertThat(slots).hasSize(15);
    }

    @Test
    void overlappingBookingReducesFreeBaysOnlyWhileItOverlaps() {
        List<BusyInterval> busy = List.of(new BusyInterval(1L, at(10, 0), at(11, 0)));

        List<SlotGrid.Slot> slots = SlotGrid.compute(DAY, IST, LocalTime.of(9, 0), LocalTime.of(12, 0),
                Duration.ofMinutes(60), List.of(1L, 2L), busy, LONG_AGO);

        assertThat(slots).extracting(SlotGrid.Slot::freeBays)
                // 09:00 09:30 10:00 10:30 11:00
                .containsExactly(2, 1, 1, 1, 2);
    }

    @Test
    void bookingsOnBaysOfTheOtherVehicleTypeAreIgnored() {
        List<BusyInterval> carBayBusy = List.of(new BusyInterval(99L, at(9, 0), at(18, 0)));

        List<SlotGrid.Slot> slots = SlotGrid.compute(DAY, IST, LocalTime.of(9, 0), LocalTime.of(10, 0),
                Duration.ofMinutes(30), List.of(4L), carBayBusy, LONG_AGO);

        assertThat(slots).allMatch(s -> s.freeBays() == 1);
    }

    @Test
    void pastSlotsAreNotOffered() {
        List<SlotGrid.Slot> slots = SlotGrid.compute(DAY, IST, LocalTime.of(9, 0), LocalTime.of(11, 0),
                Duration.ofMinutes(30), List.of(1L), List.of(), at(10, 15));

        assertThat(slots).extracting(SlotGrid.Slot::start).containsExactly(LocalTime.of(10, 30));
    }

    @Test
    void gridAcceptsOnlyHourAndHalfHour() {
        assertThat(SlotGrid.isOnGrid(LocalTime.of(9, 30))).isTrue();
        assertThat(SlotGrid.isOnGrid(LocalTime.of(9, 15))).isFalse();
        assertThat(SlotGrid.isOnGrid(LocalTime.of(9, 0, 5))).isFalse();
    }
}
