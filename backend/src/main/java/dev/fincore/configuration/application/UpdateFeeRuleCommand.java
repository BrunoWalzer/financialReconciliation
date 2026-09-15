package dev.fincore.configuration.application;

import dev.fincore.configuration.domain.RoundingMode;

public record UpdateFeeRuleCommand(int percentageBp, long fixedAmountMinor, RoundingMode roundingMode) {
}
