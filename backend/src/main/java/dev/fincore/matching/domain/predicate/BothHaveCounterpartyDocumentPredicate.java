package dev.fincore.matching.domain.predicate;

import dev.fincore.matching.domain.EvaluationContext;
import dev.fincore.matching.domain.Predicate;
import dev.fincore.matching.domain.PredicateResult;
import dev.fincore.matching.domain.RecordPair;
import java.util.Map;

/**
 * Predicado obrigatório e não negociável do Nível B (Domain §12.4, TDS 11.5, novo na v1.1):
 * ambos os registros precisam ter documento da contraparte informado, e os documentos
 * precisam ser iguais. Ausência de documento em qualquer lado <b>não é candidato</b> — nunca
 * derivado, nunca inferido de outro campo. Consequência conhecida e aceita (Implementation
 * Plan DR-2, Opção A): a fonte de liquidação do MVP não carrega documento, então este nível
 * não produz correspondência automática no par do MVP. Isto é comportamento seguro, não
 * defeito, e não deve ser relaxado para aumentar a quantidade de matches.
 */
public final class BothHaveCounterpartyDocumentPredicate implements Predicate {

    public static final String NAME = "BOTH_HAVE_COUNTERPARTY_DOCUMENT";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public PredicateResult test(RecordPair pair, EvaluationContext context) {
        String leftDocument = pair.left().counterpartyDocument();
        String rightDocument = pair.right().counterpartyDocument();
        Map<String, Object> detail = Map.of(
                "leftPresent", leftDocument != null,
                "rightPresent", rightDocument != null);

        if (leftDocument == null || rightDocument == null) {
            return PredicateResult.fail(NAME, detail);
        }
        return leftDocument.equals(rightDocument)
                ? PredicateResult.pass(NAME, detail)
                : PredicateResult.fail(NAME, detail);
    }
}
