package dev.fincore.configuration.api;

import dev.fincore.configuration.domain.SettlementWindow;
import java.util.UUID;

public record SettlementWindowResponse(
        UUID id, UUID sourcePairId, String paymentMethod, int minDays, int maxDays, long version) {

    public static SettlementWindowResponse from(SettlementWindow window) {
        return new SettlementWindowResponse(
                window.id(), window.sourcePairId(), window.paymentMethod(), window.minDays(), window.maxDays(),
                window.version());
    }
}
