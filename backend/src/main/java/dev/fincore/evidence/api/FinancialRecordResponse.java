package dev.fincore.evidence.api;

import dev.fincore.evidence.domain.FinancialRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FinancialRecordResponse(
        UUID id,
        UUID sourceId,
        UUID importBatchId,
        int lineNumber,
        String externalId,
        String correlationKey,
        String direction,
        String recordType,
        MoneyResponse grossAmount,
        MoneyResponse declaredFeeAmount,
        MoneyResponse netAmount,
        LocalDate businessDate,
        Instant sourceTimestamp,
        String counterpartyDocument,
        String paymentMethod,
        String description,
        String descriptionNormalized,
        String rawLine,
        String fingerprint,
        Instant createdAt,
        List<RecordIntegrityFlagResponse> flags) {

    /** Para {@code GET /records} (lista) — flags só aparecem no detalhe (Implementation Plan M7). */
    public static FinancialRecordResponse from(FinancialRecord record) {
        return from(record, List.of());
    }

    public static FinancialRecordResponse from(FinancialRecord record, List<RecordIntegrityFlagResponse> flags) {
        return new FinancialRecordResponse(
                record.id(),
                record.sourceId(),
                record.importBatchId(),
                record.lineNumber(),
                record.externalId(),
                record.correlationKey(),
                record.direction().name(),
                record.recordType().name(),
                MoneyResponse.from(record.grossAmount()),
                MoneyResponse.from(record.declaredFeeAmount()),
                MoneyResponse.from(record.netAmount()),
                record.businessDate(),
                record.sourceTimestamp(),
                record.counterpartyDocument(),
                record.paymentMethod(),
                record.description(),
                record.descriptionNormalized(),
                record.rawLine(),
                record.fingerprint(),
                record.createdAt(),
                flags);
    }
}
