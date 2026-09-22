package dev.fincore.matching.domain.predicate;

import dev.fincore.matching.domain.EvaluationContext;
import dev.fincore.matching.domain.Predicate;
import dev.fincore.matching.domain.PredicateResult;
import dev.fincore.matching.domain.RecordPair;
import java.util.Map;

/**
 * Nenhum dos dois já está em {@code match_claim} (TDS 11.3). O motor não decide isso
 * sozinho — o conjunto de reivindicados vem já carregado no {@link EvaluationContext}; a
 * exclusividade real é a PK de {@code match_claim} (I-5), não este predicado.
 */
public final class BothUnclaimedPredicate implements Predicate {

    public static final String NAME = "BOTH_UNCLAIMED";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public PredicateResult test(RecordPair pair, EvaluationContext context) {
        boolean leftClaimed = context.isClaimed(pair.left().id());
        boolean rightClaimed = context.isClaimed(pair.right().id());
        Map<String, Object> detail = Map.of("leftClaimed", leftClaimed, "rightClaimed", rightClaimed);
        return !leftClaimed && !rightClaimed ? PredicateResult.pass(NAME, detail) : PredicateResult.fail(NAME, detail);
    }
}
