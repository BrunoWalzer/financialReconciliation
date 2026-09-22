package dev.fincore.matching.domain.rule;

import dev.fincore.matching.domain.Decisiveness;
import dev.fincore.matching.domain.MatchingRule;
import dev.fincore.matching.domain.Predicate;
import dev.fincore.matching.domain.predicate.BothUnclaimedPredicate;
import dev.fincore.matching.domain.predicate.CompatibleDirectionPredicate;
import dev.fincore.matching.domain.predicate.CompatibleTypePredicate;
import dev.fincore.matching.domain.predicate.NoUnresolvedIntegrityFlagPredicate;
import dev.fincore.matching.domain.predicate.NotPreviouslyRejectedPredicate;
import dev.fincore.matching.domain.predicate.SameCurrencyPredicate;
import dev.fincore.matching.domain.predicate.WithinSettlementWindowPredicate;
import java.util.List;

/**
 * Nível C — sugestão apenas (TDS 11.6, Domain §12.5). Mesma consulta do Nível B, sem
 * documento nem meio de pagamento. <b>Nunca produz {@code MatchProposal}</b> — decisividade
 * {@code SUGGEST_ONLY} é aplicada pelo motor antes mesmo de tentar montar uma correspondência.
 * Teto de 20 candidatos por registro (TDS 11.6) — aplicado pelo motor sobre o horizonte, não
 * aqui.
 */
public final class RuleCAmountDateSuggestion implements MatchingRule {

    public static final String RULE_ID = "RULE_C_AMOUNT_DATE_SUGGESTION";
    public static final int MAX_CANDIDATES_PER_RECORD = 20;

    private static final List<Predicate> MANDATORY_PREDICATES = List.of(
            new SameCurrencyPredicate(),
            new CompatibleDirectionPredicate(),
            new CompatibleTypePredicate(),
            new BothUnclaimedPredicate(),
            new NoUnresolvedIntegrityFlagPredicate(),
            new NotPreviouslyRejectedPredicate(),
            new WithinSettlementWindowPredicate());

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public int ruleVersion() {
        return 1;
    }

    @Override
    public int priority() {
        return 30;
    }

    @Override
    public Decisiveness decisiveness() {
        return Decisiveness.SUGGEST_ONLY;
    }

    @Override
    public List<Predicate> mandatoryPredicates() {
        return MANDATORY_PREDICATES;
    }
}
