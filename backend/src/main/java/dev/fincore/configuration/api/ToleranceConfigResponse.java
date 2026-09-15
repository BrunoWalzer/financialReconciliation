package dev.fincore.configuration.api;

import dev.fincore.configuration.domain.ToleranceConfig;
import java.time.Instant;
import java.util.UUID;

public record ToleranceConfigResponse(
        UUID id,
        UUID sourcePairId,
        long absoluteAmountMinor,
        String currency,
        Long aggregateAlertThresholdMinor,
        UUID updatedBy,
        Instant updatedAt,
        long version) {

    public static ToleranceConfigResponse from(ToleranceConfig config) {
        return new ToleranceConfigResponse(
                config.id(), config.sourcePairId(), config.absoluteAmountMinor(), config.currency(),
                config.aggregateAlertThresholdMinor(), config.updatedBy(), config.updatedAt(), config.version());
    }
}
