package dev.fincore.configuration.api;

import dev.fincore.configuration.domain.FeeRule;
import dev.fincore.configuration.domain.RoundingMode;
import java.util.UUID;

public record FeeRuleResponse(
        UUID id,
        UUID sourceId,
        String paymentMethod,
        int percentageBp,
        long fixedAmountMinor,
        RoundingMode roundingMode,
        boolean active,
        long version) {

    public static FeeRuleResponse from(FeeRule rule) {
        return new FeeRuleResponse(
                rule.id(), rule.sourceId(), rule.paymentMethod(), rule.percentageBp(), rule.fixedAmountMinor(),
                rule.roundingMode(), rule.active(), rule.version());
    }
}
