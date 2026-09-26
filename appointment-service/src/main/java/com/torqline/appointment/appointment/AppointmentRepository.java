package com.torqline.appointment.appointment;

import com.torqline.appointment.slot.BusyInterval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    Optional<Appointment> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            select new com.torqline.appointment.slot.BusyInterval(a.bayId, a.slotStart, a.slotEnd)
            from Appointment a
            where a.dealerId = :dealerId
              and a.status <> com.torqline.appointment.appointment.AppointmentStatus.CANCELLED
              and a.slotStart < :to and a.slotEnd > :from
            """)
    List<BusyInterval> findBusyIntervals(@Param("dealerId") String dealerId,
                                         @Param("from") Instant from, @Param("to") Instant to);

    List<Appointment> findByDealerIdAndSlotStartBetweenOrderBySlotStart(String dealerId, Instant from, Instant to);
}
