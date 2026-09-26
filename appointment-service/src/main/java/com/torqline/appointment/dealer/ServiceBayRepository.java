package com.torqline.appointment.dealer;

import com.torqline.common.domain.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceBayRepository extends JpaRepository<ServiceBay, Long> {

    List<ServiceBay> findByDealerIdOrderById(String dealerId);

    List<ServiceBay> findByDealerIdAndVehicleTypeOrderById(String dealerId, VehicleType vehicleType);
}
