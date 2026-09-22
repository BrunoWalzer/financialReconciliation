package dev.fincore.matching.domain.predicate;

import dev.fincore.matching.domain.EvaluationContext;
import dev.fincore.matching.domain.Predicate;
import dev.fincore.matching.domain.PredicateResult;
import dev.fincore.matching.domain.RecordPair;
import java.util.Map;

/**
 * Sem recusa humana registrada para este par canônico (TDS 11.3, {@code match_rejection},
 * I-10). {@code match_rejection} só ganha escrita no M14 (resolução manual) — até lá, este
 * predicado sempre passa porque {@link EvaluationContext#rejectedPairs()} vem vazio, o que
 * é o comportamento correto, não uma simplificação.
 */
public final class NotPreviouslyRejectedPredicate implements Predicate {

    public static final String NAME = "NOT_PREVIOUSLY_REJECTED";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public PredicateResult test(RecordPair pair, EvaluationContext context) {
        boolean rejected = context.wasRejected(pair.left().id(), pair.right().id());
        Map<String, Object> detail = Map.of("rejected", rejected);
        return rejected ? PredicateResult.fail(NAME, detail) : PredicateResult.pass(NAME, detail);
    }
}
