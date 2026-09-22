package dev.fincore.matching.domain;

import dev.fincore.evidence.domain.FinancialRecord;
import java.util.List;

/**
 * Mais de um candidato satisfaria a regra — o resultado mais valioso que o motor pode
 * entregar quando não há como desempatar (Domain §12.4: "unicidade mútua e não 'melhor
 * candidato'"). Nunca é promovida a {@link MatchProposal}; nunca escolhe um vencedor.
 */
public record AmbiguitySet(FinancialRecord anchor, List<FinancialRecord> candidates, String ruleId) {

    public AmbiguitySet {
        candidates = List.copyOf(candidates);
    }
}
