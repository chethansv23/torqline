package com.torqline.appointment.dto;

import com.torqline.common.domain.ServiceType;
import com.torqline.common.domain.VehicleType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * @param slotStart start of the slot in the dealer's local time, e.g. {@code 2026-09-28T10:00}
 */
public record BookAppointmentRequest(
        @NotBlank String dealerId,
        @NotBlank @Size(max = 120) String customerName,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "must be 10-15 digits, optionally prefixed with +") String customerPhone,
        @Email String customerEmail,
        @NotNull VehicleType vehicleType,
        @NotBlank @Size(max = 20) String vehicleNumber,
        @Size(max = 40) String vehicleMake,
        @Size(max = 60) String vehicleModel,
        @NotNull ServiceType serviceType,
        @NotNull LocalDateTime slotStart,
        @Size(max = 500) String notes) {
}
