package dev.fincore.configuration.application;

import dev.fincore.configuration.domain.CoverageSchedule;

public record UpdateCoverageExpectationCommand(CoverageSchedule schedule, int graceDays) {
}
