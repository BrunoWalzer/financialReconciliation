package dev.fincore.matching.domain;

import dev.fincore.evidence.domain.FinancialRecord;
import java.util.Comparator;

/**
 * A única ordem permitida sempre que uma coleção pode ter mais de um candidato (TDS 11.7):
 * {@code businessDate}, depois {@code externalId} com nulos ao final, depois {@code id}.
 * Nunca a ordem natural do PostgreSQL, nunca a ordem de inserção.
 */
public final class DeterministicOrder {

    public static final Comparator<FinancialRecord> RECORD_ORDER = Comparator
            .comparing(FinancialRecord::businessDate)
            .thenComparing(FinancialRecord::externalId, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(FinancialRecord::id);

    private DeterministicOrder() {
    }
}
