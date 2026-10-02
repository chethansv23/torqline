package com.torqline.appointment.dto;

import com.torqline.common.domain.VehicleType;

public record BayView(Long id, String name, VehicleType vehicleType) {
}
