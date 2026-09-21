package dev.fincore.ingestion.parser;

import dev.fincore.evidence.domain.Direction;
import dev.fincore.evidence.domain.RecordType;
import dev.fincore.shared.money.Money;
import java.time.Instant;
import java.time.LocalDate;

/** O rascunho tipado que uma linha aceita produz — tudo que {@code FinancialRecord} precisa, exceto identidade e proveniência de importação. */
public record NormalizedRecordDraft(
        String externalId,
        String correlationKey,
        Direction direction,
        RecordType recordType,
        Money grossAmount,
        Money declaredFeeAmount,
        Money netAmount,
        LocalDate businessDate,
        Instant sourceTimestamp,
        String counterpartyDocument,
        String paymentMethod,
        String description,
        String descriptionNormalized,
        String fingerprint) {
}
