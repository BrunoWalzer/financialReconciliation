package dev.fincore.configuration.application;

public record UpdateSettlementWindowCommand(int minDays, int maxDays) {
}
