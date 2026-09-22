package dev.fincore.matching.domain.predicate;

import dev.fincore.evidence.domain.RecordType;
import dev.fincore.matching.domain.EvaluationContext;
import dev.fincore.matching.domain.Predicate;
import dev.fincore.matching.domain.PredicateResult;
import dev.fincore.matching.domain.RecordPair;
import java.util.Map;
import java.util.Set;

/**
 * Venda↔liquidação, estorno↔estorno. <b>Nunca</b> venda↔estorno (TDS 11.3) — a segunda das
 * duas defesas para o mesmo erro, porque o custo dele é alto e o custo dela é nulo.
 */
public final class CompatibleTypePredicate implements Predicate {

    public static final String NAME = "COMPATIBLE_TYPE";

    private static final Set<Set<RecordType>> COMPATIBLE_PAIRS = Set.of(
            Set.of(RecordType.SALE, RecordType.SETTLEMENT),
            Set.of(RecordType.REFUND));

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public PredicateResult test(RecordPair pair, EvaluationContext context) {
        RecordType left = pair.left().recordType();
        RecordType right = pair.right().recordType();
        Map<String, Object> detail = Map.of("left", left.name(), "right", right.name());
        // Set.of(a, b) lança IllegalArgumentException quando a == b (proíbe elemento
        // duplicado) — left == right é exatamente o caso estorno↔estorno, então a
        // combinação não pode ser normalizada com Set.of aqui.
        Set<RecordType> combination = left == right ? Set.of(left) : Set.of(left, right);
        boolean compatible = COMPATIBLE_PAIRS.contains(combination);
        return compatible ? PredicateResult.pass(NAME, detail) : PredicateResult.fail(NAME, detail);
    }
}
