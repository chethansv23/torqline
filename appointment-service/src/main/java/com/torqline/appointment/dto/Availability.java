package com.torqline.appointment.dto;

import com.torqline.appointment.slot.SlotGrid;
import com.torqline.common.domain.ServiceType;
import com.torqline.common.domain.VehicleType;

import java.time.LocalDate;
import java.util.List;

public record Availability(String dealerId, LocalDate date, VehicleType vehicleType, ServiceType serviceType,
                           long durationMinutes, List<SlotGrid.Slot> slots) {
}
