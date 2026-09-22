package dev.fincore.matching.domain.predicate;

import dev.fincore.matching.domain.EvaluationContext;
import dev.fincore.matching.domain.Predicate;
import dev.fincore.matching.domain.PredicateResult;
import dev.fincore.matching.domain.RecordPair;
import java.util.Map;

/**
 * Nenhum dos dois tem flag de integridade aberta (TDS 11.3) — o registro sobre o qual o
 * sistema aprendeu algo (M7) não é combinado automaticamente até alguém resolver a flag.
 */
public final class NoUnresolvedIntegrityFlagPredicate implements Predicate {

    public static final String NAME = "NO_UNRESOLVED_INTEGRITY_FLAG";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public PredicateResult test(RecordPair pair, EvaluationContext context) {
        var leftFlags = context.openFlagsFor(pair.left().id());
        var rightFlags = context.openFlagsFor(pair.right().id());
        Map<String, Object> detail = Map.of("leftFlags", leftFlags, "rightFlags", rightFlags);
        return leftFlags.isEmpty() && rightFlags.isEmpty()
                ? PredicateResult.pass(NAME, detail)
                : PredicateResult.fail(NAME, detail);
    }
}
