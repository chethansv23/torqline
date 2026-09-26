package com.torqline.appointment.appointment;

import com.torqline.appointment.dealer.Dealer;
import com.torqline.appointment.dealer.DealerRepository;
import com.torqline.appointment.dealer.ServiceBayRepository;
import com.torqline.appointment.slot.BusySlotCache;
import com.torqline.common.domain.ServiceType;
import com.torqline.common.domain.VehicleType;
import com.torqline.common.messaging.OutboxWriter;
import com.torqline.common.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Booking validation rules, without a database. Concurrency is covered by BookingConcurrencyTest. */
@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    /** 2030-01-07 08:00 IST: before opening on the day the tests book. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-07T02:30:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime MONDAY_10AM = LocalDateTime.of(2030, 1, 7, 10, 0);

    @Mock AppointmentRepository appointments;
    @Mock DealerRepository dealers;
    @Mock ServiceBayRepository bays;
    @Mock BusySlotCache cache;
    @Mock OutboxWriter outbox;
    @Mock PlatformTransactionManager txManager;
    @Mock JdbcTemplate jdbc;

    AppointmentService service;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(appointments, dealers, bays, cache, outbox, txManager, jdbc, CLOCK);
    }

    private void indiranagarOpen9to6() {
        Dealer dealer = BeanUtils.instantiateClass(Dealer.class);
        ReflectionTestUtils.setField(dealer, "id", "TQ-BLR-IND");
        ReflectionTestUtils.setField(dealer, "name", "Torqline Indiranagar");
        ReflectionTestUtils.setField(dealer, "timezone", "Asia/Kolkata");
        ReflectionTestUtils.setField(dealer, "openTime", LocalTime.of(9, 0));
        ReflectionTestUtils.setField(dealer, "closeTime", LocalTime.of(18, 0));
        when(dealers.findById("TQ-BLR-IND")).thenReturn(Optional.of(dealer));
    }

    private static BookAppointmentRequest request(VehicleType vehicle, ServiceType service, LocalDateTime start) {
        return new BookAppointmentRequest("TQ-BLR-IND", "Asha", "9845000001", null, vehicle, "KA03 hb 1234",
                null, null, service, start, null);
    }

    private void assertRejected(BookAppointmentRequest request, String code) {
        assertThatThrownBy(() -> service.book(request, null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo(code);
        verify(appointments, never()).saveAndFlush(any());
    }

    @Test
    void rejectsSlotsOffTheHalfHourGrid() {
        indiranagarOpen9to6();
        assertRejected(request(VehicleType.BIKE, ServiceType.OIL_CHANGE, MONDAY_10AM.withMinute(15)), "OFF_GRID");
    }

    @Test
    void rejectsJobsThatWouldRunPastClosingTime() {
        indiranagarOpen9to6();
        // A car general service takes 2 hours, so 16:30 would finish at 18:30.
        assertRejected(request(VehicleType.CAR, ServiceType.GENERAL_SERVICE, MONDAY_10AM.withHour(16).withMinute(30)),
                "OUTSIDE_HOURS");
    }

    @Test
    void rejectsSlotsBeforeOpening() {
        indiranagarOpen9to6();
        assertRejected(request(VehicleType.BIKE, ServiceType.OIL_CHANGE, MONDAY_10AM.withHour(8).withMinute(30)),
                "OUTSIDE_HOURS");
    }

    @Test
    void rejectsSlotsInThePast() {
        indiranagarOpen9to6();
        assertRejected(request(VehicleType.BIKE, ServiceType.OIL_CHANGE, MONDAY_10AM.minusDays(1)), "SLOT_IN_PAST");
    }

    @Test
    void rejectsJobsNotOfferedForTheVehicle() {
        assertRejected(request(VehicleType.CAR, ServiceType.CHAIN_SPROCKET, MONDAY_10AM), "SERVICE_NOT_OFFERED");
    }

    @Test
    void unknownDealerIsNotFound() {
        when(dealers.findById("TQ-BLR-IND")).thenReturn(Optional.empty());
        assertRejected(request(VehicleType.BIKE, ServiceType.OIL_CHANGE, MONDAY_10AM), "NOT_FOUND");
    }

    @Test
    void dealerWithoutBaysForTheVehicleIsRejected() {
        indiranagarOpen9to6();
        when(bays.findByDealerIdAndVehicleTypeOrderById("TQ-BLR-IND", VehicleType.BIKE)).thenReturn(java.util.List.of());
        assertRejected(request(VehicleType.BIKE, ServiceType.OIL_CHANGE, MONDAY_10AM), "NO_BAYS");
    }

    @Test
    void repeatedIdempotencyKeyReturnsExistingBookingWithoutInserting() {
        indiranagarOpen9to6();
        BookAppointmentRequest request = request(VehicleType.BIKE, ServiceType.OIL_CHANGE, MONDAY_10AM);
        Appointment existing = new Appointment("TQ-BLR-IND", 4L, request,
                Instant.parse("2030-01-07T04:30:00Z"), Instant.parse("2030-01-07T05:00:00Z"), "key-1");
        when(appointments.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        AppointmentService.BookingResult result = service.book(request, "key-1");

        assertThat(result.replayed()).isTrue();
        assertThat(result.appointment().id()).isEqualTo(existing.getId());
        assertThat(result.appointment().localStart()).isEqualTo(MONDAY_10AM);
        assertThat(result.appointment().vehicleNumber()).isEqualTo("KA03HB1234");
        verify(appointments, never()).saveAndFlush(any());
    }

    @Test
    void onlyBookedAppointmentsCanBeCheckedInOrCancelled() {
        Appointment appointment = new Appointment("TQ-BLR-IND", 4L,
                request(VehicleType.BIKE, ServiceType.OIL_CHANGE, MONDAY_10AM), Instant.now(), Instant.now(), null);
        appointment.cancel("Customer travelling");

        assertThatThrownBy(() -> appointment.checkIn(1000)).isInstanceOf(ApiException.class)
                .hasMessageContaining("CANCELLED");
        assertThatThrownBy(() -> appointment.cancel("again")).isInstanceOf(ApiException.class);
    }
}
