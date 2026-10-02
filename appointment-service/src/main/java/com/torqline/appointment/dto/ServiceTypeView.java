package com.torqline.appointment.dto;

import com.torqline.common.domain.ServiceType;
import com.torqline.common.domain.VehicleType;

import java.util.Map;

public record ServiceTypeView(ServiceType code, String description, Map<VehicleType, Long> durationMinutes) {
}
