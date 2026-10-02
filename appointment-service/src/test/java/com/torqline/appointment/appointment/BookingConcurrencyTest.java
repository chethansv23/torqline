package com.torqline.appointment.appointment;

import com.torqline.appointment.dto.BookAppointmentRequest;
import com.torqline.appointment.dto.BookingResult;
import com.torqline.common.domain.ServiceType;
import com.torqline.common.domain.VehicleType;
import com.torqline.common.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the double-booking guarantee against a real Postgres: many customers race for the same
 * slot and exactly as many succeed as there are bays.
 */
@SpringBootTest(properties = {
        "torqline.outbox.relay-enabled=false",
        "torqline.cache.enabled=false",
        "spring.kafka.admin.auto-create=false",
        // Let every racing thread hold a connection at once, as a busy multi-instance deployment would.
        "spring.datasource.hikari.maximum-pool-size=60"
})
@Testcontainers
class BookingConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    AppointmentService service;

    @Autowired
    JdbcTemplate jdbc;

    private static final String DEALER = "TQ-BLR-IND"; // 2 bike stands, 3 car lifts

    private static BookAppointmentRequest bikeService(LocalDateTime slot, String phoneSuffix) {
        return new BookAppointmentRequest(DEALER, "Rider " + phoneSuffix, "98450" + phoneSuffix, null,
                VehicleType.BIKE, "KA01EX" + phoneSuffix, "Royal Enfield", "Classic 350",
                ServiceType.GENERAL_SERVICE, slot, null);
    }

    private static LocalDateTime slotInDays(int days, int hour) {
        return LocalDate.now().plusDays(days).atTime(LocalTime.of(hour, 0));
    }

    @Test
    void concurrentRequestsCannotOverbookBays() throws Exception {
        LocalDateTime slot = slotInDays(7, 10);
        int customers = 50;
        CountDownLatch startGun = new CountDownLatch(1);
        List<Callable<UUID>> attempts = new ArrayList<>();
        for (int i = 0; i < customers; i++) {
            String suffix = String.format("%05d", i);
            attempts.add(() -> {
                startGun.await();
                return service.book(bikeService(slot, suffix), null).appointment().id();
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(customers);
        List<Future<UUID>> results = attempts.stream().map(pool::submit).toList();
        startGun.countDown();

        int booked = 0;
        int rejected = 0;
        for (Future<UUID> result : results) {
            try {
                result.get();
                booked++;
            } catch (Exception e) {
                assertThat(e.getCause()).isInstanceOf(ApiException.class)
                        .extracting(c -> ((ApiException) c).code()).isEqualTo("SLOT_UNAVAILABLE");
                rejected++;
            }
        }
        pool.shutdown();

        assertThat(booked).isEqualTo(2);
        assertThat(rejected).isEqualTo(customers - 2);
        assertThat(jdbc.queryForObject("""
                select count(distinct bay_id) from appointment
                where status = 'BOOKED' and vehicle_type = 'BIKE' and slot_start = ?
                """, Long.class, java.sql.Timestamp.from(slot.atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant())))
                .isEqualTo(2L);
    }

    /** Same race at the four-stand Whitefield branch with a short job, the shape that exposed the deadlock under k6. */
    @Test
    void concurrentRequestsCannotOverbookFourBays() throws Exception {
        LocalDateTime slot = slotInDays(11, 15);
        int customers = 50;
        CountDownLatch startGun = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(customers);
        List<Future<UUID>> results = new ArrayList<>();
        for (int i = 0; i < customers; i++) {
            String suffix = String.format("%05d", 30000 + i);
            results.add(pool.submit(() -> {
                startGun.await();
                var request = new BookAppointmentRequest("TQ-BLR-WHF", "Rider " + suffix, "98450" + suffix, null,
                        VehicleType.BIKE, "KA53EZ" + suffix, null, null, ServiceType.OIL_CHANGE, slot, null);
                return service.book(request, null).appointment().id();
            }));
        }
        startGun.countDown();

        int booked = 0;
        List<Throwable> unexpected = new ArrayList<>();
        for (Future<UUID> result : results) {
            try {
                result.get();
                booked++;
            } catch (Exception e) {
                if (!(e.getCause() instanceof ApiException api && api.code().equals("SLOT_UNAVAILABLE"))) {
                    unexpected.add(e.getCause());
                }
            }
        }
        pool.shutdown();

        assertThat(unexpected).as("only clean 409s are acceptable, got e.g. %s",
                unexpected.isEmpty() ? "none" : unexpected.get(0)).isEmpty();
        assertThat(booked).isEqualTo(4);
    }

    @Test
    void retryWithSameIdempotencyKeyReturnsOriginalBooking() {
        LocalDateTime slot = slotInDays(8, 11);
        String key = UUID.randomUUID().toString();

        BookingResult first = service.book(bikeService(slot, "11111"), key);
        BookingResult retry = service.book(bikeService(slot, "11111"), key);

        assertThat(first.replayed()).isFalse();
        assertThat(retry.replayed()).isTrue();
        assertThat(retry.appointment().id()).isEqualTo(first.appointment().id());
        assertThat(jdbc.queryForObject("select count(*) from outbox_event where aggregate_id = ?",
                Long.class, first.appointment().id().toString())).isEqualTo(1L);
    }

    @Test
    void cancellingFreesTheBayForSomeoneElse() {
        LocalDateTime slot = slotInDays(9, 14);
        UUID a = service.book(bikeService(slot, "20001"), null).appointment().id();
        service.book(bikeService(slot, "20002"), null);
        assertThatThrownBy(() -> service.book(bikeService(slot, "20003"), null)).isInstanceOf(ApiException.class);

        service.cancel(a, "Customer travelling");

        assertThat(service.book(bikeService(slot, "20003"), null).appointment().status())
                .isEqualTo(AppointmentStatus.BOOKED);
    }

    @Test
    void carOnlyJobCannotBeBookedForABike() {
        var request = new BookAppointmentRequest(DEALER, "Rider", "9845012345", null, VehicleType.BIKE,
                "KA01AB1234", null, null, ServiceType.WHEEL_ALIGNMENT, slotInDays(10, 10), null);

        assertThatThrownBy(() -> service.book(request, null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo("SERVICE_NOT_OFFERED");
    }
}
