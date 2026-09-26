package com.torqline.inventory.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    boolean existsByRequestId(UUID requestId);

    List<Reservation> findByRepairOrderIdOrderByCreatedAt(UUID repairOrderId);

    List<Reservation> findByRepairOrderIdAndStatus(UUID repairOrderId, ReservationStatus status);
}
