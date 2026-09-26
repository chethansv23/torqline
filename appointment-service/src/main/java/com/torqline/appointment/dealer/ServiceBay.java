package com.torqline.appointment.dealer;

import com.torqline.common.domain.VehicleType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A physical workstation. Car lifts and bike stands are not interchangeable, so each bay has one vehicle type. */
@Entity
@Table(name = "service_bay")
public class ServiceBay {

    @Id
    private Long id;
    private String dealerId;
    private String name;
    @Enumerated(EnumType.STRING)
    private VehicleType vehicleType;

    protected ServiceBay() {
    }

    public Long getId() {
        return id;
    }

    public String getDealerId() {
        return dealerId;
    }

    public String getName() {
        return name;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }
}
