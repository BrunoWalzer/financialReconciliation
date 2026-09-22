package dev.fincore.matching.domain.predicate;

import dev.fincore.evidence.domain.RecordIntegrityFlagType;
import dev.fincore.matching.domain.EvaluationContext;
import dev.fincore.matching.domain.Predicate;
import dev.fincore.matching.domain.PredicateResult;
import dev.fincore.matching.domain.RecordPair;
import java.util.Map;

/**
 * Predicado adicional do Nível A (TDS 11.4): a chave de correlação aparece em exatamente um
 * registro de cada lado. <b>Lido da flag de integridade, nunca recalculado por escopo</b> —
 * a duplicidade de chave é propriedade da fonte, já detectada na importação (M7, TDS 9.6).
 * Reavaliar aqui abriria uma segunda forma de calcular a mesma coisa, com o risco de as duas
 * divergirem.
 */
public final class KeyUniqueBothSidesPredicate implements Predicate {

    public static final String NAME = "KEY_UNIQUE_BOTH_SIDES";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public PredicateResult test(RecordPair pair, EvaluationContext context) {
        boolean leftDuplicated = context.openFlagsFor(pair.left().id()).contains(RecordIntegrityFlagType.DUPLICATE_CORRELATION_KEY);
        boolean rightDuplicated = context.openFlagsFor(pair.right().id()).contains(RecordIntegrityFlagType.DUPLICATE_CORRELATION_KEY);
        Map<String, Object> detail = Map.of(
                "key", String.valueOf(pair.left().correlationKey()),
                "leftDuplicated", leftDuplicated,
                "rightDuplicated", rightDuplicated);
        return !leftDuplicated && !rightDuplicated
                ? PredicateResult.pass(NAME, detail)
                : PredicateResult.fail(NAME, detail);
    }
}
