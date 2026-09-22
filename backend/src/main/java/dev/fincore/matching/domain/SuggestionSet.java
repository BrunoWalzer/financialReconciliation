package dev.fincore.matching.domain;

import dev.fincore.evidence.domain.FinancialRecord;
import java.util.List;

/**
 * Nível C (Domain §12.5): lista ordenada de candidatos, nunca uma correspondência. Score,
 * se existir, serve só para ordenar esta lista — nunca para decidir (Domain §12.6).
 */
public record SuggestionSet(FinancialRecord anchor, List<FinancialRecord> candidates, String ruleId) {

    public SuggestionSet {
        candidates = List.copyOf(candidates);
    }
}
