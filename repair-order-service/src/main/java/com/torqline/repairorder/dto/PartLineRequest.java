package com.torqline.repairorder.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record PartLineRequest(@NotBlank String sku, @Min(1) @Max(50) int quantity) {
}
