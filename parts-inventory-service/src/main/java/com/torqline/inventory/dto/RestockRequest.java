package com.torqline.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RestockRequest(@Min(1) @Max(10_000) int quantity) {
}
