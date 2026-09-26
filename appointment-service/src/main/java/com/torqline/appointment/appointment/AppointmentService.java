package com.torqline.appointment.appointment;

import com.torqline.appointment.dealer.Dealer;
import com.torqline.appointment.dealer.DealerRepository;
import com.torqline.appointment.dealer.ServiceBay;
import com.torqline.appointment.dealer.ServiceBayRepository;
import com.torqline.appointment.slot.BusyInterval;
import com.torqline.appointment.slot.BusySlotCache;
import com.torqline.appointment.slot.SlotGrid;
import com.torqline.common.domain.ServiceType;
import com.torqline.common.domain.VehicleType;
import com.torqline.common.events.AppointmentEvents.AppointmentBooked;
import com.torqline.common.events.AppointmentEvents.AppointmentCancelled;
import com.torqline.common.events.AppointmentEvents.AppointmentCheckedIn;
import com.torqline.common.events.Topics;
import com.torqline.common.messaging.OutboxWriter;
import com.torqline.common.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    /** Postgres SQLSTATE for exclusion_violation: the bay already has an overlapping booking. */
    private static final String EXCLUSION_VIOLATION = "23P01";
    /** Namespace for {@code pg_advisory_xact_lock(namespace, bayId)} so these locks cannot collide with others. */
    private static final int BAY_LOCK_NAMESPACE = 7001;
    /** Postgres SQLSTATE for unique_violation: here, a concurrent request with the same Idempotency-Key. */
    private static final String UNIQUE_VIOLATION = "23505";

    public record BookingResult(AppointmentResponse appointment, boolean replayed) {
    }

    public record Availability(String dealerId, LocalDate date, VehicleType vehicleType, ServiceType serviceType,
                               long durationMinutes, List<SlotGrid.Slot> slots) {
    }

    private final AppointmentRepository appointments;
    private final DealerRepository dealers;
    private final ServiceBayRepository bays;
    private final BusySlotCache busyCache;
    private final OutboxWriter outbox;
    private final TransactionTemplate tx;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointments, DealerRepository dealers, ServiceBayRepository bays,
                              BusySlotCache busyCache, OutboxWriter outbox, PlatformTransactionManager txManager,
                              JdbcTemplate jdbc, Clock clock) {
        this.appointments = appointments;
        this.dealers = dealers;
        this.bays = bays;
        this.busyCache = busyCache;
        this.outbox = outbox;
        this.tx = new TransactionTemplate(txManager);
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Availability availability(String dealerId, VehicleType vehicleType, ServiceType serviceType, LocalDate date) {
        Dealer dealer = dealer(dealerId);
        Duration duration = durationOf(serviceType, vehicleType);
        List<Long> bayIds = bays.findByDealerIdAndVehicleTypeOrderById(dealerId, vehicleType).stream()
                .map(ServiceBay::getId).toList();
        List<BusyInterval> busy = busyIntervals(dealer, date);
        List<SlotGrid.Slot> slots = SlotGrid.compute(date, dealer.zone(), dealer.getOpenTime(), dealer.getCloseTime(),
                duration, bayIds, busy, clock.instant());
        return new Availability(dealerId, date, vehicleType, serviceType, duration.toMinutes(), slots);
    }

    /**
     * Books the first free bay of the right vehicle type. The database, not this method, is the
     * arbiter of conflicts: an exclusion constraint rejects overlapping bookings on the same bay, so
     * two concurrent requests for the last free bay cannot both win, even across service instances.
     */
    public BookingResult book(BookAppointmentRequest request, String idempotencyKey) {
        if (idempotencyKey != null) {
            var existing = appointments.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return replay(existing.get());
            }
        }
        Duration duration = durationOf(request.serviceType(), request.vehicleType());
        Dealer dealer = dealer(request.dealerId());
        Instant start = validateSlot(dealer, request.slotStart(), duration);
        Instant end = start.plus(duration);

        List<ServiceBay> candidates = bays.findByDealerIdAndVehicleTypeOrderById(dealer.getId(), request.vehicleType());
        if (candidates.isEmpty()) {
            throw ApiException.badRequest("NO_BAYS", dealer.getName() + " has no " + request.vehicleType() + " bays");
        }
        // Try bays that look free first; the constraint still guards against races.
        Set<Long> busyBays = busyIntervals(dealer, request.slotStart().toLocalDate()).stream()
                .filter(b -> b.overlaps(start, end)).map(BusyInterval::bayId).collect(Collectors.toSet());
        List<ServiceBay> ordered = candidates.stream()
                .sorted(Comparator.comparing((ServiceBay b) -> busyBays.contains(b.getId()))).toList();

        for (ServiceBay bay : ordered) {
            try {
                Appointment saved = tx.execute(s -> insert(bay, request, start, end, idempotencyKey));
                busyCache.evict(dealer.getId(), request.slotStart().toLocalDate());
                log.info("Booked appointment {} on bay {} at {}", saved.getId(), bay.getName(), request.slotStart());
                return new BookingResult(AppointmentResponse.of(saved, dealer.zone()), false);
            } catch (DataIntegrityViolationException e) {
                if (hasSqlState(e, EXCLUSION_VIOLATION)) {
                    log.debug("Bay {} taken for {}, trying next", bay.getId(), request.slotStart());
                    continue;
                }
                if (idempotencyKey != null && hasSqlState(e, UNIQUE_VIOLATION)) {
                    return replay(appointments.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> e));
                }
                throw e;
            }
        }
        throw ApiException.conflict("SLOT_UNAVAILABLE",
                "No " + request.vehicleType() + " bay is free at " + request.slotStart() + " for " + request.serviceType());
    }

    /**
     * The per-bay advisory lock is what makes the exclusion constraint safe under contention. Without
     * it, two transactions inserting overlapping rows for the same bay at the same moment each wait on
     * the other's uncommitted row while checking the constraint, and Postgres aborts one with a
     * deadlock (found by the k6 race test; see docs/AI-REVIEW.md). With it, inserts for one bay
     * queue up, and each one after the winner fails fast with a clean exclusion violation. The lock is
     * released at commit or rollback, and each transaction holds at most one, so no cycle can form.
     */
    private Appointment insert(ServiceBay bay, BookAppointmentRequest request, Instant start, Instant end, String key) {
        jdbc.query("select pg_advisory_xact_lock(?, ?)", rs -> null, BAY_LOCK_NAMESPACE, bay.getId().intValue());
        Appointment appointment = appointments.saveAndFlush(
                new Appointment(bay.getDealerId(), bay.getId(), request, start, end, key));
        outbox.append(Topics.APPOINTMENT_EVENTS, "Appointment", appointment.getId(), new AppointmentBooked(
                appointment.getId(), appointment.getDealerId(), appointment.getCustomerName(),
                appointment.getCustomerPhone(), appointment.getVehicleType(), appointment.getVehicleNumber(),
                appointment.getServiceType(), start, end));
        return appointment;
    }

    public AppointmentResponse get(UUID id) {
        Appointment a = appointment(id);
        return AppointmentResponse.of(a, dealer(a.getDealerId()).zone());
    }

    public List<AppointmentResponse> forDay(String dealerId, LocalDate date) {
        ZoneId zone = dealer(dealerId).zone();
        return appointments.findByDealerIdAndSlotStartBetweenOrderBySlotStart(dealerId,
                        date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant())
                .stream().map(a -> AppointmentResponse.of(a, zone)).toList();
    }

    public AppointmentResponse checkIn(UUID id, Integer odometerKm) {
        return update(id, a -> {
            a.checkIn(odometerKm);
            outbox.append(Topics.APPOINTMENT_EVENTS, "Appointment", a.getId(), new AppointmentCheckedIn(
                    a.getId(), a.getDealerId(), a.getCustomerName(), a.getCustomerPhone(), a.getVehicleType(),
                    a.getVehicleNumber(), a.getVehicleMake(), a.getVehicleModel(), a.getServiceType(), odometerKm));
        });
    }

    public AppointmentResponse cancel(UUID id, String reason) {
        return update(id, a -> {
            a.cancel(reason);
            outbox.append(Topics.APPOINTMENT_EVENTS, "Appointment", a.getId(), new AppointmentCancelled(
                    a.getId(), a.getDealerId(), a.getCustomerName(), a.getCustomerPhone(), a.getSlotStart(), reason));
        });
    }

    private AppointmentResponse update(UUID id, Consumer<Appointment> change) {
        Appointment updated = tx.execute(s -> {
            Appointment a = appointment(id);
            change.accept(a);
            return appointments.saveAndFlush(a);
        });
        ZoneId zone = dealer(updated.getDealerId()).zone();
        busyCache.evict(updated.getDealerId(), LocalDateTime.ofInstant(updated.getSlotStart(), zone).toLocalDate());
        return AppointmentResponse.of(updated, zone);
    }

    private Instant validateSlot(Dealer dealer, LocalDateTime localStart, Duration duration) {
        if (!SlotGrid.isOnGrid(localStart.toLocalTime())) {
            throw ApiException.badRequest("OFF_GRID", "Slots start on the hour or half hour");
        }
        LocalDateTime localEnd = localStart.plus(duration);
        if (localStart.toLocalTime().isBefore(dealer.getOpenTime())
                || !localEnd.toLocalDate().equals(localStart.toLocalDate())
                || localEnd.toLocalTime().isAfter(dealer.getCloseTime())) {
            throw ApiException.badRequest("OUTSIDE_HOURS", dealer.getName() + " is open "
                    + dealer.getOpenTime() + "-" + dealer.getCloseTime() + " and this job takes "
                    + duration.toMinutes() + " minutes");
        }
        Instant start = localStart.atZone(dealer.zone()).toInstant();
        if (start.isBefore(clock.instant())) {
            throw ApiException.badRequest("SLOT_IN_PAST", "Cannot book a slot in the past");
        }
        return start;
    }

    private List<BusyInterval> busyIntervals(Dealer dealer, LocalDate date) {
        ZoneId zone = dealer.zone();
        return busyCache.get(dealer.getId(), date, () -> appointments.findBusyIntervals(dealer.getId(),
                date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant()));
    }

    private BookingResult replay(Appointment existing) {
        return new BookingResult(AppointmentResponse.of(existing, dealer(existing.getDealerId()).zone()), true);
    }

    private static Duration durationOf(ServiceType serviceType, VehicleType vehicleType) {
        if (!serviceType.supports(vehicleType)) {
            throw ApiException.badRequest("SERVICE_NOT_OFFERED", serviceType + " is not offered for a " + vehicleType);
        }
        return serviceType.durationFor(vehicleType);
    }

    private Dealer dealer(String id) {
        return dealers.findById(id).orElseThrow(() -> ApiException.notFound("Dealer", id));
    }

    private Appointment appointment(UUID id) {
        return appointments.findById(id).orElseThrow(() -> ApiException.notFound("Appointment", id));
    }

    private static boolean hasSqlState(Throwable e, String sqlState) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sqlState.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
