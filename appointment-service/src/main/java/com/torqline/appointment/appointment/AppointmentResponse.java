package com.torqline.appointment.appointment;

import com.torqline.common.domain.ServiceType;
import com.torqline.common.domain.VehicleType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

public record AppointmentResponse(
        UUID id, String dealerId, Long bayId, AppointmentStatus status,
        String customerName, String customerPhone, VehicleType vehicleType, String vehicleNumber,
        String vehicleMake, String vehicleModel, ServiceType serviceType,
        LocalDateTime localStart, LocalDateTime localEnd, Instant slotStart, Instant slotEnd,
        String notes, Integer odometerKm, String cancelReason) {

    public static AppointmentResponse of(Appointment a, ZoneId zone) {
        return new AppointmentResponse(a.getId(), a.getDealerId(), a.getBayId(), a.getStatus(),
                a.getCustomerName(), a.getCustomerPhone(), a.getVehicleType(), a.getVehicleNumber(),
                a.getVehicleMake(), a.getVehicleModel(), a.getServiceType(),
                LocalDateTime.ofInstant(a.getSlotStart(), zone), LocalDateTime.ofInstant(a.getSlotEnd(), zone),
                a.getSlotStart(), a.getSlotEnd(), a.getNotes(), a.getOdometerKm(), a.getCancelReason());
    }
}
