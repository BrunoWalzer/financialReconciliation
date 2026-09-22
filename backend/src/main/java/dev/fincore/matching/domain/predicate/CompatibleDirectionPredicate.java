package dev.fincore.matching.domain.predicate;

import dev.fincore.matching.domain.EvaluationContext;
import dev.fincore.matching.domain.Predicate;
import dev.fincore.matching.domain.PredicateResult;
import dev.fincore.matching.domain.RecordPair;
import java.util.Map;

/** Mesma direção nos dois lados (TDS 11.3) — uma das duas defesas contra venda↔estorno. */
public final class CompatibleDirectionPredicate implements Predicate {

    public static final String NAME = "COMPATIBLE_DIRECTION";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public PredicateResult test(RecordPair pair, EvaluationContext context) {
        var left = pair.left().direction();
        var right = pair.right().direction();
        if (left == right) {
            return PredicateResult.pass(NAME, Map.of("both", left.name()));
        }
        return PredicateResult.fail(NAME, Map.of("left", left.name(), "right", right.name()));
    }
}
