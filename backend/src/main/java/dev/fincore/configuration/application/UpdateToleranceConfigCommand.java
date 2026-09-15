package dev.fincore.configuration.application;

/** O que muda numa tolerância — "a operação mais sensível do sistema" (Domain §27.2). */
public record UpdateToleranceConfigCommand(long absoluteAmountMinor, String currency, Long aggregateAlertThresholdMinor) {
}
