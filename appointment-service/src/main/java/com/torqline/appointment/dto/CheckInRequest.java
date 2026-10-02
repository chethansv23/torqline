package com.torqline.appointment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CheckInRequest(@Min(0) @Max(2_000_000) Integer odometerKm) {
}
