package dev.fincore.matching.domain.rule;

import dev.fincore.matching.domain.Decisiveness;
import dev.fincore.matching.domain.MatchingRule;
import dev.fincore.matching.domain.Predicate;
import dev.fincore.matching.domain.predicate.BothUnclaimedPredicate;
import dev.fincore.matching.domain.predicate.CompatibleDirectionPredicate;
import dev.fincore.matching.domain.predicate.CompatibleTypePredicate;
import dev.fincore.matching.domain.predicate.KeyUniqueBothSidesPredicate;
import dev.fincore.matching.domain.predicate.NoUnresolvedIntegrityFlagPredicate;
import dev.fincore.matching.domain.predicate.NotPreviouslyRejectedPredicate;
import dev.fincore.matching.domain.predicate.SameCurrencyPredicate;
import java.util.List;

/**
 * Nível A — chave de correlação (TDS 11.4, Domain §12.3). Seleção: chave de correlação
 * igual e não vazia nos dois lados. {@code WITHIN_SETTLEMENT_WINDOW} <b>não</b> é mandatório
 * aqui, de propósito: "Fora da janela, resto ok" ainda produz correspondência automática
 * (TDS 11.4) — quando a identidade está provada pela chave, o motor decide e registra o
 * prazo como informação, não como bloqueio. É avaliado e aparece na evidência mesmo assim.
 */
public final class RuleACorrelationKey implements MatchingRule {

    public static final String RULE_ID = "RULE_A_CORRELATION_KEY";

    private static final List<Predicate> MANDATORY_PREDICATES = List.of(
            new SameCurrencyPredicate(),
            new CompatibleDirectionPredicate(),
            new CompatibleTypePredicate(),
            new BothUnclaimedPredicate(),
            new NoUnresolvedIntegrityFlagPredicate(),
            new NotPreviouslyRejectedPredicate(),
            new KeyUniqueBothSidesPredicate());

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
        return 10;
    }

    @Override
    public Decisiveness decisiveness() {
        return Decisiveness.AUTO_MATCH;
    }

    @Override
    public List<Predicate> mandatoryPredicates() {
        return MANDATORY_PREDICATES;
    }
}
