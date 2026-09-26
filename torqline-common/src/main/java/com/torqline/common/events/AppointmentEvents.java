package com.torqline.common.events;

import com.torqline.common.domain.ServiceType;
import com.torqline.common.domain.VehicleType;

import java.time.Instant;
import java.util.UUID;

/** Events published by appointment-service on {@link Topics#APPOINTMENT_EVENTS}, keyed by appointment id. */
public final class AppointmentEvents {

    public record AppointmentBooked(
            UUID appointmentId, String dealerId, String customerName, String customerPhone,
            VehicleType vehicleType, String vehicleNumber, ServiceType serviceType,
            Instant slotStart, Instant slotEnd) {
    }

    public record AppointmentCancelled(
            UUID appointmentId, String dealerId, String customerName, String customerPhone,
            Instant slotStart, String reason) {
    }

    public record AppointmentCheckedIn(
            UUID appointmentId, String dealerId, String customerName, String customerPhone,
            VehicleType vehicleType, String vehicleNumber, String vehicleMake, String vehicleModel,
            ServiceType serviceType, Integer odometerKm) {
    }

    private AppointmentEvents() {
    }
}
