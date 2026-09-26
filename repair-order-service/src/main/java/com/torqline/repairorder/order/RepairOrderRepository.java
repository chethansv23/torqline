package com.torqline.repairorder.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RepairOrderRepository extends JpaRepository<RepairOrder, UUID> {

    boolean existsByAppointmentId(UUID appointmentId);

    List<RepairOrder> findByDealerIdOrderByOpenedAtDesc(String dealerId);

    List<RepairOrder> findByDealerIdAndStatusOrderByOpenedAtDesc(String dealerId, RepairOrderStatus status);
}
