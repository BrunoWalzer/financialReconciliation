package dev.fincore.configuration.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateToleranceConfigRequest(
        @NotNull @Min(0) Long absoluteAmountMinor,
        @NotBlank String currency,
        Long aggregateAlertThresholdMinor) {
}
