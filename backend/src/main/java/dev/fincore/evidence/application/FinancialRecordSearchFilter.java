package dev.fincore.evidence.application;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Filtros de {@code GET /records} (TDS 20.2), todos opcionais. {@code sourceId} já chega
 * resolvido de {@code sourceCode} — ver {@code evidence.api}.
 */
public record FinancialRecordSearchFilter(
        UUID sourceId,
        String externalId,
        String correlationKey,
        Long grossAmountMinor,
        LocalDate businessDateFrom,
        LocalDate businessDateTo,
        String paymentMethod,
        String counterpartyDocument) {
}
