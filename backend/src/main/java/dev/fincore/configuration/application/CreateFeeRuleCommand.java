package dev.fincore.configuration.application;

import dev.fincore.configuration.domain.RoundingMode;
import java.util.UUID;

public record CreateFeeRuleCommand(
        UUID sourceId, String paymentMethod, int percentageBp, long fixedAmountMinor, RoundingMode roundingMode) {
}
