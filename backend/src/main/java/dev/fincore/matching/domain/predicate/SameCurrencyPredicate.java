package dev.fincore.matching.domain.predicate;

import dev.fincore.matching.domain.EvaluationContext;
import dev.fincore.matching.domain.Predicate;
import dev.fincore.matching.domain.PredicateResult;
import dev.fincore.matching.domain.RecordPair;
import java.util.Map;

/** Moedas idênticas (TDS 11.3). Nenhum câmbio, nenhuma conversão dentro do motor. */
public final class SameCurrencyPredicate implements Predicate {

    public static final String NAME = "SAME_CURRENCY";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public PredicateResult test(RecordPair pair, EvaluationContext context) {
        var left = pair.left().currency();
        var right = pair.right().currency();
        Map<String, Object> detail = Map.of("left", left.name(), "right", right.name());
        return left == right ? PredicateResult.pass(NAME, detail) : PredicateResult.fail(NAME, detail);
    }
}
