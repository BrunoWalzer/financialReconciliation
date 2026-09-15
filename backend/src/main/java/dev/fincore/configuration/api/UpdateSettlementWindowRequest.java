package dev.fincore.configuration.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateSettlementWindowRequest(@NotNull @Min(0) Integer minDays, @NotNull @Min(0) Integer maxDays) {
}
