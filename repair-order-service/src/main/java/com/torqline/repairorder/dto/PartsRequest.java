package com.torqline.repairorder.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PartsRequest(@NotEmpty @Size(max = 20) List<@Valid PartLineRequest> lines) {
}
