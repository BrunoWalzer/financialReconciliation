package dev.fincore.configuration.api;

import dev.fincore.configuration.domain.RoundingMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateFeeRuleRequest(
        @NotNull @Min(0) @Max(10_000) Integer percentageBp,
        @NotNull @Min(0) Long fixedAmountMinor,
        @NotNull RoundingMode roundingMode) {
}
