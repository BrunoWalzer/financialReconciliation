package dev.fincore.configuration.api;

import dev.fincore.configuration.domain.CoverageExpectation;
import dev.fincore.configuration.domain.CoverageSchedule;
import java.util.UUID;

public record CoverageExpectationResponse(
        UUID id, UUID sourceId, CoverageSchedule schedule, int graceDays, boolean active, long version) {

    public static CoverageExpectationResponse from(CoverageExpectation expectation) {
        return new CoverageExpectationResponse(
                expectation.id(), expectation.sourceId(), expectation.schedule(), expectation.graceDays(),
                expectation.active(), expectation.version());
    }
}
