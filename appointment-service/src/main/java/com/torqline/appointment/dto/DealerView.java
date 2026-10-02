package com.torqline.appointment.dto;

import com.torqline.common.domain.VehicleType;

import java.time.LocalTime;
import java.util.Map;

public record DealerView(String id, String name, String city, String timezone, LocalTime openTime,
                         LocalTime closeTime, Map<VehicleType, Long> bayCount) {
}
