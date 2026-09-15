package dev.fincore.configuration.api;

import dev.fincore.configuration.domain.CoverageSchedule;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateCoverageExpectationRequest(@NotNull CoverageSchedule schedule, @NotNull @Min(0) Integer graceDays) {
}
